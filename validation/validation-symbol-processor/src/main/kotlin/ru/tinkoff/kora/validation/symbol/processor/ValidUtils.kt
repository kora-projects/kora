package ru.tinkoff.kora.validation.symbol.processor

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.squareup.kotlinpoet.ksp.toClassName

class ValidUtils {

    companion object {

        fun getConstraints(type: KSTypeReference, annotation: Sequence<KSAnnotation>): List<Constraint> {
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
}
