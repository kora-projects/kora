package io.koraframework.validation.symbol.processor

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
