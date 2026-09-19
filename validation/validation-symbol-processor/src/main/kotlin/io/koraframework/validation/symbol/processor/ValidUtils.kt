package io.koraframework.validation.symbol.processor

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.symbol.*
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
}
