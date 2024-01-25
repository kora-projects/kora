package ru.tinkoff.kora.ksp.common

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSTypeReference
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import ru.tinkoff.kora.ksp.common.KspCommonUtils.resolveToUnderlying
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


    fun KSAnnotated.isAnnotationPresent(type: ClassName) = this.findAnnotations(type).firstOrNull() != null

    fun KSAnnotated.isAnnotationPresent(predicate: (ClassName) -> Boolean): Boolean {
        return this.annotations.any { predicate(it.annotationType.resolveToUnderlying().toClassName()) }
    }

    fun KSAnnotated.findAnnotation(type: ClassName) = this.annotations
        // try to avoid excessive resolve by finding exact match first (simple name + fqn)
        .filter { it.shortName.getShortName() == type.simpleName }
        .filter { it.annotationType.resolve().declaration.qualifiedName?.asString() == type.canonicalName }
        .ifEmpty { this.findAnnotations(type) } // no exact matches - look for all matches (including typealiases)
        .firstOrNull()

    fun KSAnnotated.findAnnotations(type: ClassName) = this.annotations
        .filter { it.annotationType.resolveToUnderlying().declaration.qualifiedName?.asString() == type.canonicalName }

    fun KSAnnotated.findAnnotation(type: KClass<out Annotation>) = findAnnotation(type.asClassName())

    // todo list of class names?
    inline fun <reified T> KSAnnotation.findValue(name: String) = this.arguments.asSequence()
        .filter { it.name!!.asString() == name }
        .map { it.value!! }
        .map { it as T }
        .firstOrNull()

    inline fun <reified T> KSAnnotation.findValueNoDefault(name: String): T? {
        val defaultValues = defaultArguments
        return this.arguments.asSequence()
            .filter { it.name!!.asString() == name }
            .filter { !defaultValues.contains(it) }
            .map { it.value!! }
            .map { it as T }
            .firstOrNull()
    }
}
