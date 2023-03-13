package ru.tinkoff.kora.validation.symbol.processor

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference

class ValidUtils {

    companion object {

        fun getConstraints(type: KSTypeReference, annotation: Sequence<KSAnnotation>): List<Constraint> {
            return annotation
                .mapNotNull { origin ->
                    origin.annotationType.resolve().declaration.annotations
                        .filter { a -> a.annotationType.asType().canonicalName() == VALIDATED_BY_TYPE.canonicalName }
                        .map { validatedBy ->
                            val parameters = origin.arguments.associate { a -> Pair(a.name!!.asString(), a.value!!) }
                            val factory = validatedBy.arguments
                                .filter { arg -> arg.name!!.getShortName() == "value" }
                                .map { arg -> arg.value as KSType }
                                .first()

                            Constraint(
                                origin.annotationType.asType(),
                                Constraint.Factory(factory.declaration.qualifiedName!!.asString().asType(listOf(type.resolve().makeNullable().asType())), parameters)
                            )
                        }
                        .firstOrNull()
                }
                .toList()
        }
    }
}
