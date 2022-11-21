package ru.tinkoff.kora.ksp.common

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSType

object TagUtils {
    val ignoreList = setOf("Component", "DefaultComponent")

    fun parseTagValue(target: KSAnnotated): Set<String> {
        for (annotation in target.annotations.filter { !ignoreList.contains(it.shortName.asString()) }) {
            val type = annotation.annotationType.resolve()
            if (type.declaration.qualifiedName!!.asString() == CommonClassNames.tag.canonicalName) {
                return AnnotationUtils.parseAnnotationValueWithoutDefaults<List<KSType>>(annotation, "value")!!
                    .asSequence()
                    .map { it.declaration.qualifiedName!!.asString() }
                    .toSet()
            }
            for (annotatedWith in type.declaration.annotations) {
                val type = annotatedWith.annotationType.resolve()
                if (type.declaration.qualifiedName!!.asString() == CommonClassNames.tag.canonicalName) {
                    return AnnotationUtils.parseAnnotationValueWithoutDefaults<List<KSType>>(annotatedWith, "value")!!
                        .asSequence()
                        .map { it.declaration.qualifiedName!!.asString() }
                        .toSet()
                }

            }
        }
        return setOf()
    }
}
