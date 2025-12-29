package ru.tinkoff.kora.validation.symbol.processor

import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ksp.toClassName
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFlow
import ru.tinkoff.kora.validation.symbol.processor.ValidTypes.VALIDATED_BY_TYPE

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
        val isJsonNullable = type.declaration.let { it as KSClassDeclaration }.toClassName() == ValidTypes.jsonNullable
        val realType = if (isJsonNullable) type.arguments[0].type!!.resolve() else type

        return annotation
            .mapNotNull { origin ->
                origin.annotationType.resolve().declaration.annotations
                    .filter { a -> a.annotationType.resolve().declaration.let { it as KSClassDeclaration }.toClassName() == VALIDATED_BY_TYPE }
                    .map { validatedBy ->
                        val parameters = origin.arguments.associate { a -> Pair(a.name!!.asString(), a.value!!) }
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
}
