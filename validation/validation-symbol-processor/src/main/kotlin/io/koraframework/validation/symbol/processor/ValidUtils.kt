package io.koraframework.validation.symbol.processor

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ksp.toClassName
import io.koraframework.ksp.common.FunctionUtils.isFlow
import io.koraframework.validation.symbol.processor.ValidTypes.VALIDATED_BY_TYPE

object ValidUtils {

    fun KSPropertyDeclaration.getConstraints(): List<Constraint> {
        val type = this.type.resolve()
        val typeUseConstraints = collectTypeUse(type).constraints
        val constraints = getDirectConstraints(type, this.annotations)
        if (constraints.isNotEmpty()) {
            return constraints + typeUseConstraints
        }

        val classDecl = this.parentDeclaration as KSClassDeclaration
        classDecl.primaryConstructor?.let {
            it.parameters
                .filter { it.name?.asString() == this.simpleName.asString() }
                .firstOrNull()
                ?.let {
                    return getDirectConstraints(type, it.annotations) + typeUseConstraints
                }
        }
        return typeUseConstraints
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

    private fun getConstraints(type: KSType, annotation: Sequence<KSAnnotation>): List<Constraint> {
        return getDirectConstraints(type, annotation) + collectTypeUse(type).constraints
    }

    /**
     * @return `@Valid` put on type arguments of a container, like `List<@Valid Item>` or `Map<String, @Valid Item>`
     */
    fun getTypeUseValidated(type: KSType): List<Validated> = collectTypeUse(type).validated

    private class TypeUseValidation(val constraints: MutableList<Constraint> = ArrayList(), val validated: MutableList<Validated> = ArrayList())

    private val iterableNames = setOf("kotlin.collections.Iterable", "java.lang.Iterable")
    private val mapNames = setOf("kotlin.collections.Map", "java.util.Map")

    private fun collectTypeUse(type: KSType): TypeUseValidation {
        val isJsonNullable = type.declaration.let { if (it is KSClassDeclaration) it.toClassName() else null } == ValidTypes.jsonNullable
        val realType = if (isJsonNullable) type.arguments[0].type!!.resolve() else type
        val result = TypeUseValidation()
        collectTypeUse(realType.makeNotNullable().asDeepType(), realType, listOf(), result)
        return result
    }

    private fun collectTypeUse(root: Type, type: KSType, containers: List<Container>, result: TypeUseValidation) {
        val declaration = type.declaration as? KSClassDeclaration ?: return
        val names = declaration.getAllSuperTypes().mapNotNull { it.declaration.qualifiedName?.asString() }.toSet() + declaration.qualifiedName?.asString()
        val argumentContainers = when {
            type.arguments.size == 1 && names.any { it in iterableNames } -> listOf(Container.ITERABLE)
            type.arguments.size == 2 && names.any { it in mapNames } -> listOf(Container.MAP_KEYS, Container.MAP_VALUES)
            else -> return
        }

        for ((i, argument) in type.arguments.withIndex()) {
            val argumentReference = argument.type ?: continue
            val argumentType = argumentReference.resolve()
            val argumentPath = containers + argumentContainers[i]
            val annotations = (argument.annotations + argumentReference.annotations + argumentType.annotations)
                .distinctBy { it.toString() + it.arguments }
                .toList()

            for (constraint in getDirectConstraints(argumentType, annotations.asSequence())) {
                result.constraints.add(constraint.copy(factory = constraint.factory.copy(root = root, containers = argumentPath)))
            }
            if (annotations.any { it.annotationType.resolve().declaration.qualifiedName?.asString() == ValidTypes.VALID_TYPE.canonicalName }) {
                result.validated.add(Validated(argumentType.makeNotNullable().asDeepType(), root, argumentPath))
            }
            collectTypeUse(root, argumentType, argumentPath, result)
        }
    }

    /**
     * Keeps type arguments at every level, like `Map<String, List<Item>>`
     */
    private fun KSType.asDeepType(): Type = this.asType().copy(generic = this.arguments.mapNotNull { it.type?.resolve()?.asDeepType() })

    private fun getDirectConstraints(type: KSType, annotation: Sequence<KSAnnotation>): List<Constraint> {
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
}
