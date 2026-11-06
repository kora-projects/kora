package io.koraframework.validation.symbol.processor

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.toClassName
import io.koraframework.ksp.common.FunctionUtils.isFlow
import io.koraframework.validation.symbol.processor.ValidTypes.VALIDATED_BY_TYPE

object ValidUtils {

    fun KSPropertyDeclaration.getConstraints(): List<Constraint> {
        val type = this.type.resolve()
        val constraints = getConstraints(type, this.annotations)
        if (constraints.isNotEmpty()) {
            return constraints
        }

        val classDecl = this.parentDeclaration as KSClassDeclaration
        classDecl.primaryConstructor?.let {
            it.parameters
                .filter { it.name?.asString() == this.simpleName.asString() }
                .firstOrNull()
                ?.let {
                    return getConstraints(type, it.annotations)
                }
        }
        return listOf()
    }

    fun KSValueParameter.getConstraints(): List<Constraint> {
        val type = this.type
        return getConstraints(type.resolve(), this.annotations)
    }

    fun KSFunctionDeclaration.getConstraints(): List<Constraint> {
        val returnTypeReference = if (this.isFlow())
            this.returnType!!.resolve().arguments.first().type!!
        else
            this.returnType!!

        return getConstraints(returnTypeReference.resolve(), this.annotations)
    }

    /**
     * @return validation put on type arguments of a collection or a map, like `List<@Valid Item>` or `Map<String, @Size(max = 5) String>`
     */
    fun getTypeUseValidated(type: KSType): List<Validated> {
        val isJsonNullable = type.declaration.let { if (it is KSClassDeclaration) it.toClassName() else null } == ValidTypes.jsonNullable
        val realType = if (isJsonNullable) type.arguments[0].type!!.resolve() else type
        val children = getTypeArgumentsValidation(realType)
        if (children.isEmpty()) {
            return listOf()
        }
        return listOf(Validated(realType.makeNotNullable().asDeepType(), TypeUse(listOf(), listOf(), children), realType.makeNotNullable()))
    }

    private val iterableNames = setOf("kotlin.collections.Iterable", "java.lang.Iterable")
    private val mapNames = setOf("kotlin.collections.Map", "java.util.Map")

    private fun getTypeArgumentsValidation(type: KSType): Map<Container, TypeUse> {
        val declaration = type.declaration as? KSClassDeclaration ?: return mapOf()
        val names = declaration.getAllSuperTypes().mapNotNull { it.declaration.qualifiedName?.asString() }.toSet() + declaration.qualifiedName?.asString()
        val containers = when {
            type.arguments.size == 1 && names.any { it in iterableNames } -> listOf(Container.ITERABLE)
            type.arguments.size == 2 && names.any { it in mapNames } -> listOf(Container.MAP_KEYS, Container.MAP_VALUES)
            else -> return mapOf()
        }

        val result = LinkedHashMap<Container, TypeUse>()
        for ((i, argument) in type.arguments.withIndex()) {
            val argumentReference = argument.type ?: continue
            val argumentType = argumentReference.resolve()
            val annotations = (argument.annotations + argumentReference.annotations + argumentType.annotations)
                .distinctBy { it.toString() + it.arguments }
                .toList()

            val constraints = getConstraints(argumentType, annotations.asSequence())
            val validated = if (annotations.any { it.annotationType.resolve().declaration.qualifiedName?.asString() == ValidTypes.VALID_TYPE.canonicalName })
                listOf(argumentType.makeNotNullable().asDeepType())
            else
                listOf()
            val typeUse = TypeUse(constraints, validated, getTypeArgumentsValidation(argumentType))
            if (!typeUse.isEmpty()) {
                result[containers[i]] = typeUse
            }
        }
        return result
    }

    /**
     * Keeps type arguments at every level, like `Map<String, List<Item>>`
     */
    private fun KSType.asDeepType(): Type = this.asType().copy(generic = this.arguments.mapNotNull { it.type?.resolve()?.asDeepType() })

    private fun getConstraints(type: KSType, annotation: Sequence<KSAnnotation>): List<Constraint> {
        val isJsonNullable = type.declaration.let { if (it is KSClassDeclaration) it.toClassName() else null } == ValidTypes.jsonNullable
        val realType = if (isJsonNullable) type.arguments[0].type!!.resolve() else type

        return annotation
            .mapNotNull { origin ->
                origin.annotationType.resolve().declaration.annotations
                    .filter { a -> a.annotationType.resolve().declaration.let { it as KSClassDeclaration }.toClassName() == VALIDATED_BY_TYPE }
                    .map { validatedBy ->
                        val parameters = origin.parametersInDeclarationOrder()
                        val factory = validatedBy.arguments
                            .filter { arg -> arg.name!!.getShortName() == "value" }
                            .map { arg -> arg.value as KSType }
                            .first()

                        Constraint(
                            origin.annotationType.asType(),
                            Constraint.Factory(factory.declaration.qualifiedName!!.asString().asType(listOf(realType.makeNotNullable().asType())), parameters)
                        )
                    }
                    .firstOrNull()
            }
            .toList()
    }

    /**
     * Factory arguments are passed positionally, so the order must follow the annotation declaration
     * and not the order the members were written at the use site: `@Size(max = 5)` has to become
     * `create(0, 5)` and never `create(5, 0)`.
     */
    private fun KSAnnotation.parametersInDeclarationOrder(): Map<String, Any> {
        val declarationOrder = annotationType.resolve().declaration.let { it as KSClassDeclaration }
            .memberNamesInDeclarationOrder()
            .withIndex()
            .associate { (index, name) -> name to index }

        return arguments
            .sortedBy { declarationOrder[it.name?.asString()] ?: Int.MAX_VALUE }
            .associate { a -> Pair(a.name!!.asString(), a.value!!) }
    }

    /** Members of a Kotlin annotation are constructor parameters, of a Java one — abstract methods. */
    private fun KSClassDeclaration.memberNamesInDeclarationOrder(): List<String> {
        val constructorParameters = primaryConstructor?.parameters.orEmpty()
        if (constructorParameters.isNotEmpty()) {
            return constructorParameters.mapNotNull { it.name?.asString() }
        }

        return getDeclaredFunctions()
            .filter { it.isAbstract }
            .map { it.simpleName.asString() }
            .toList()
    }

    fun parameterCode(value: Any?): CodeBlock {
        return when (value) {
            is String -> CodeBlock.of("%S", value)
            is KSClassDeclaration if value.classKind == ClassKind.ENUM_ENTRY -> CodeBlock.of("%T.%N", (value.parentDeclaration as KSClassDeclaration).toClassName(), value.simpleName.asString())
            is List<*> -> CodeBlock.of("arrayOf(%L)", value.map { parameterCode(it) }.joinToCode(", "))
            is Double if value.isNaN() -> CodeBlock.of("%T.NaN", DOUBLE)
            is Double if value.isInfinite() -> CodeBlock.of(if (value > 0) "%T.POSITIVE_INFINITY" else "%T.NEGATIVE_INFINITY", DOUBLE)
            is Float if value.isNaN() -> CodeBlock.of("%T.NaN", FLOAT)
            is Float if value.isInfinite() -> CodeBlock.of(if (value > 0) "%T.POSITIVE_INFINITY" else "%T.NEGATIVE_INFINITY", FLOAT)
            // -9_223_372_036_854_775_808 is not a valid Kotlin literal: the unary minus is applied to an out of range number
            is Long if value == Long.MIN_VALUE -> CodeBlock.of("%T.MIN_VALUE", LONG)
            else -> CodeBlock.of("%L", value)
        }
    }
}
