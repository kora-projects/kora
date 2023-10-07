package ru.tinkoff.kora.validation.symbol.processor

import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ksp.toClassName
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFlow

object ValidUtils {
    fun KSPropertyDeclaration.getConstraints(): List<Constraint> {
        val type = this.type
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
        return getConstraints(type, this.annotations)
    }

    fun KSFunctionDeclaration.getConstraints(): List<Constraint> {
        val returnTypeReference = if (this.isFlow())
            this.returnType!!.resolve().arguments.first().type!!
        else
            this.returnType!!

        return getConstraints(returnTypeReference, this.annotations)
    }


    private fun getConstraints(type: KSTypeReference, annotation: Sequence<KSAnnotation>): List<Constraint> {
        return annotation
            .mapNotNull { origin ->
                origin.annotationType.resolve().declaration.annotations
                    .filter { a -> a.annotationType.resolve().toClassName() == VALIDATED_BY_TYPE }
                    .map { validatedBy ->
                        val parameters = origin.arguments.associate { a -> Pair(a.name!!.asString(), a.value!!) }
                        val factory = validatedBy.arguments
                            .filter { arg -> arg.name!!.getShortName() == "value" }
                            .map { arg -> arg.value as KSType }
                            .first()

                        Constraint(
                            origin.annotationType.asType(),
                            Constraint.Factory(factory.declaration.qualifiedName!!.asString().asType(listOf(type.resolve().makeNotNullable().asType())), parameters)
                        )
                    }
                    .firstOrNull()
            }
            .toList()
    }
}
