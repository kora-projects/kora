package io.koraframework.validation.symbol.processor

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.toClassName
import io.koraframework.ksp.common.FunctionUtils.isFlow
import io.koraframework.validation.symbol.processor.ValidTypes.VALIDATED_BY_TYPE

object ValidUtils {

    fun KSPropertyDeclaration.getConstraints(): List<Constraint> {
        val type = this.type
        val constraints = getConstraints(type.resolve(), this.annotations)
        if (constraints.isNotEmpty()) {
            return constraints
        }

        val classDecl = this.parentDeclaration as KSClassDeclaration
        classDecl.primaryConstructor?.let {
            it.parameters
                .filter { it.name?.asString() == this.simpleName.asString() }
                .firstOrNull()
                ?.let {
                    return getConstraints(type.resolve(), it.annotations)
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

                        val validated = realType.makeNotNullable().asType()
                        val factoryName = factory.declaration.qualifiedName!!.asString()
                        val factoryType = if ((factory.declaration as KSClassDeclaration).typeParameters.isEmpty())
                            factoryName.asType()
                        else
                            factoryName.asType(listOf(validated))

                        Constraint(
                            origin.annotationType.asType(),
                            Constraint.Factory(factoryType, validated, parameters)
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
    private fun KSAnnotation.parametersInDeclarationOrder(): Map<String, CodeBlock> {
        val members = annotationType.resolve().declaration.let { it as KSClassDeclaration }.membersInDeclarationOrder()
        val declarationOrder = members.keys.withIndex().associate { (index, name) -> name to index }

        return arguments
            .sortedBy { declarationOrder[it.name?.asString()] ?: Int.MAX_VALUE }
            .associate { a -> Pair(a.name!!.asString(), parameterCode(a.value!!, members[a.name!!.asString()])) }
    }

    /** Members of a Kotlin annotation are constructor parameters, of a Java one — abstract methods. */
    private fun KSClassDeclaration.membersInDeclarationOrder(): Map<String, KSType?> {
        val constructorParameters = primaryConstructor?.parameters.orEmpty()
        if (constructorParameters.isNotEmpty()) {
            return constructorParameters.filter { it.name != null }.associate { it.name!!.asString() to it.type.resolve() }
        }

        return getDeclaredFunctions()
            .filter { it.isAbstract }
            .associate { it.simpleName.asString() to it.returnType?.resolve() }
    }

    private val primitiveArrays = setOf("Int", "Long", "Short", "Byte", "Char", "Boolean", "Float", "Double")
        .associate { "kotlin.${it}Array" to "${it.lowercase()}ArrayOf" }

    /** Renders an annotation argument as a Kotlin literal assignable to the declared annotation member type. */
    private fun parameterCode(value: Any?, declaredType: KSType?): CodeBlock {
        return when (value) {
            is String -> CodeBlock.of("%S", value)
            is Float -> CodeBlock.of("%Lf", value)
            is Char -> CodeBlock.of("'\\u%L'", "%04x".format(value.code))
            is KSType -> CodeBlock.of("%T::class", value.toClassName())
            is KSClassDeclaration if value.classKind == ClassKind.ENUM_ENTRY -> CodeBlock.of("%T.%N", (value.parentDeclaration as KSClassDeclaration).toClassName(), value.simpleName.asString())
            is List<*> -> CodeBlock.of(
                "%L(%L)",
                primitiveArrays[declaredType?.declaration?.qualifiedName?.asString()] ?: "arrayOf",
                value.map { parameterCode(it, null) }.joinToCode(", ")
            )
            else -> CodeBlock.of("%L", value)
        }
    }
}
