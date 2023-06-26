package ru.tinkoff.kora.ksp.common

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterSpec

object TagUtils {
    val ignoreList = setOf("Component", "DefaultComponent")

    fun KSAnnotated.parseTags(): Set<String> {
        return TagUtils.parseTagValue(this)
    }

    fun KSType.parseTags(): Set<String> {
        return TagUtils.parseTagValue(this)
    }

    fun ParameterSpec.Builder.tag(tag: Set<String>): ParameterSpec.Builder {
        if (tag.isEmpty()) {
            return this
        }
        return this.addAnnotation(tag.toTagAnnotation())
    }

    fun parseTagValue(target: KSAnnotated): Set<String> {
        return parseTagValue(target.annotations)
    }

    fun parseTagValue(target: KSType): Set<String> {
        return parseTagValue(target.annotations)
    }

    fun parseTagValue(annotations: Sequence<KSAnnotation>): Set<String> {
        for (annotation in annotations.filter { !ignoreList.contains(it.shortName.asString()) }) {
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

    fun Collection<String>.toTagAnnotation(): AnnotationSpec {
        val codeBlock = CodeBlock.builder().add("value = [")
        forEachIndexed { i, type ->
            if (i > 0) {
                codeBlock.add(", ")
            }
            codeBlock.add("%L::class", type)
        }
        val value = codeBlock.add("]").build()
        return AnnotationSpec.builder(CommonClassNames.tag).addMember(value).build()
    }
}
