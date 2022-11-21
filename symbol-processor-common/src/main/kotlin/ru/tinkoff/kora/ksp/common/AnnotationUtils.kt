package ru.tinkoff.kora.ksp.common

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSTypeReference
import com.squareup.kotlinpoet.ClassName
import kotlin.reflect.KClass

object AnnotationUtils {
    inline fun <reified T> parseAnnotationValueWithoutDefaults(annotation: KSAnnotation?, name: String): T? {
        if (annotation == null) {
            return null
        }
        for (argument in annotation.arguments) {
            if (argument.name!!.asString() == name) {
                val value = argument.value ?: return null
                if (value is List<*>) {
                    return value.asSequence()
                        .map { if (it is KSTypeReference) it.resolve() else it }
                        .toList() as T
                }
                return value as T
            }
        }
        return null
    }


    fun KSAnnotated.findAnnotation(type: ClassName) = this.findAnnotations(type).firstOrNull()

    fun KSAnnotated.findAnnotations(type: ClassName) = this.annotations
        .filter { it.shortName.getShortName() == type.simpleName }
        .filter { it.annotationType.resolve().declaration.qualifiedName?.asString() == type.canonicalName }

    fun KSAnnotated.findAnnotation(type: KClass<out Annotation>): KSAnnotation? {
        val name = type.qualifiedName!!
        return this.annotations
            .filter { it.annotationType.resolve().declaration.qualifiedName!!.asString() == name }
            .firstOrNull()
    }


    // todo list of class names?
    inline fun <reified T> KSAnnotation.findValue(name: String) = this.arguments.asSequence()
        .filter { it.name!!.asString() == name }
        .map { it.value!! }
        .map { it as T }
        .firstOrNull()
}
