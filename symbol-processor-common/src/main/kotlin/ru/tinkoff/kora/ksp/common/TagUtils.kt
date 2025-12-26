package ru.tinkoff.kora.ksp.common

import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*

object TagUtils {
    val ignoreList = setOf("Component", "DefaultComponent")

    fun KSAnnotated.parseTag(): String? {
        return parseTagValue(this)
    }

    fun KSType.parseTag(): String? {
        return parseTagValue(this)
    }

    fun FunSpec.Builder.addTag(tag: String?): FunSpec.Builder {
        if (tag == null) {
            return this
        }
        return this.addAnnotation(tag.toTagAnnotation())
    }

    fun FunSpec.Builder.addTag(tag: TypeName?): FunSpec.Builder {
        if (tag == null) {
            return this
        }
        return this.addAnnotation(tag.toTagAnnotation())
    }

    fun ParameterSpec.Builder.addTag(tag: String?): ParameterSpec.Builder {
        if (tag == null) {
            return this
        }
        return this.addAnnotation(tag.toTagAnnotation())
    }


    fun ParameterSpec.Builder.addTag(tag: TypeName?): ParameterSpec.Builder {
        if (tag == null) {
            return this
        }
        return this.addAnnotation(tag.toTagAnnotation())
    }

    fun TypeSpec.Builder.addTag(tag: String?): TypeSpec.Builder {
        if (tag == null) {
            return this
        }
        return this.addAnnotation(tag.toTagAnnotation())
    }

    fun parseTagValue(target: KSAnnotated): String? {
        val tag = parseTagValue(target.annotations)
        if (tag != null) {
            return tag
        }
        if (target is KSPropertyDeclaration) {
            target.parentDeclaration?.let {
                if (it is KSClassDeclaration && it.modifiers.contains(Modifier.DATA)) {
                    it.primaryConstructor?.let {
                        val constructorParameter = it.parameters.find { it.name?.asString() == target.simpleName.asString() }
                        if (constructorParameter != null) {
                            return parseTagValue(constructorParameter)
                        }
                    }
                }
            }
        }
        return null
    }

    fun parseTagValue(target: KSType): String? {
        return parseTagValue(target.annotations)
    }

    fun parseTagValue(annotations: Sequence<KSAnnotation>): String? {
        for (annotation in annotations.filter { !ignoreList.contains(it.shortName.asString()) }) {
            val type = annotation.annotationType.resolve()
            if (type.declaration.qualifiedName?.asString() == CommonClassNames.tag.canonicalName) {
                return AnnotationUtils.parseAnnotationValueWithoutDefaults<KSType>(annotation, "value")!!
                    .declaration.qualifiedName!!.asString()
            }
            for (annotatedWith in type.declaration.annotations) {
                val annotatedWithType = annotatedWith.annotationType.resolve()
                if (annotatedWithType.declaration.qualifiedName?.asString() == CommonClassNames.tag.canonicalName) {
                    return AnnotationUtils.parseAnnotationValueWithoutDefaults<KSType>(annotatedWith, "value")!!
                        .declaration.qualifiedName!!.asString()
                }

            }
        }
        return null
    }

    fun String.toTagAnnotation(): AnnotationSpec {
        val codeBlock = CodeBlock.builder()
            .add("%L::class", this)
            .build()
        return AnnotationSpec.builder(CommonClassNames.tag).addMember(codeBlock).build()
    }

    fun TypeName.toTagAnnotation(): AnnotationSpec {
        return AnnotationSpec.builder(CommonClassNames.tag)
            .addMember("%T::class", this)
            .build()
    }

    fun String?.tagMatches(other: String?): Boolean {
        if (this == null && other == null) {
            return true
        }
        if (this == null) {
            return false
        }
        if (this == CommonClassNames.tagAny.canonicalName) {
            return true
        }
        return this == other
    }
}
