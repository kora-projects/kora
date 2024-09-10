package ru.tinkoff.kora.ksp.common

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec

object TagUtils {
    val ignoreList = setOf("Component", "DefaultComponent")

    fun KSAnnotated.parseTags(): Set<String> {
        return parseTagValue(this)
    }

    fun KSType.parseTags(): Set<String> {
        return parseTagValue(this)
    }

    fun FunSpec.Builder.addTag(tag: Set<String>): FunSpec.Builder {
        if (tag.isEmpty()) {
            return this
        }
        return this.addAnnotation(tag.toTagAnnotation())
    }

    fun ParameterSpec.Builder.addTag(tag: Set<String>): ParameterSpec.Builder {
        if (tag.isEmpty()) {
            return this
        }
        return this.addAnnotation(tag.toTagAnnotation())
    }

    fun ParameterSpec.Builder.addTag(vararg tags: TypeName): ParameterSpec.Builder {
        if (tags.isEmpty()) {
            return this
        }
        return this.addAnnotation(tags.toList().toTagTypesAnnotation())
    }

    fun ParameterSpec.Builder.addTag(tag: Collection<TypeName>): ParameterSpec.Builder {
        if (tag.isEmpty()) {
            return this
        }
        return this.addAnnotation(tag.toTagSpecTypes())
    }

    fun TypeSpec.Builder.addTag(tag: Set<String>): TypeSpec.Builder {
        if (tag.isEmpty()) {
            return this
        }
        return this.addAnnotation(tag.toTagAnnotation())
    }

    fun parseTagValue(target: KSAnnotated): Set<String> {
        val tag = parseTagValue(target.annotations)
        if (tag.isNotEmpty()) {
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
        return setOf()
    }

    fun parseTagValue(target: KSType): Set<String> {
        return parseTagValue(target.annotations)
    }

    fun parseTagValue(annotations: Sequence<KSAnnotation>): Set<String> {
        for (annotation in annotations.filter { !ignoreList.contains(it.shortName.asString()) }) {
            val type = annotation.annotationType.resolve()
            if (type.declaration.qualifiedName?.asString() == CommonClassNames.tag.canonicalName) {
                return AnnotationUtils.parseAnnotationValueWithoutDefaults<List<KSType>>(annotation, "value")!!
                    .asSequence()
                    .map { it.declaration.qualifiedName!!.asString() }
                    .toSet()
            }
            for (annotatedWith in type.declaration.annotations) {
                val annotatedWithType = annotatedWith.annotationType.resolve()
                if (annotatedWithType.declaration.qualifiedName?.asString() == CommonClassNames.tag.canonicalName) {
                    return AnnotationUtils.parseAnnotationValueWithoutDefaults<List<KSType>>(annotatedWith, "value")!!
                        .asSequence()
                        .map { it.declaration.qualifiedName!!.asString() }
                        .toSet()
                }

            }
        }
        return setOf()
    }

    fun Collection<TypeName>.toTagSpecTypes(): AnnotationSpec {
        return if (size == 1) {
            first().toTagTypeAnnotation()
        } else {
            val codeBlock = CodeBlock.builder().add("value = [")
            forEachIndexed { i, type ->
                if (i > 0) {
                    codeBlock.add(", ")
                }
                codeBlock.add("%T::class", type)
            }
            val value = codeBlock.add("]").build()
            return AnnotationSpec.builder(CommonClassNames.tag).addMember(value).build()
        }
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

    fun Collection<TypeName>.toTagTypesAnnotation(): AnnotationSpec {
        if(size == 1) {
            return AnnotationSpec.builder(CommonClassNames.tag)
                .addMember("%T::class", this.first())
                .build()
        } else {
            val codeBlock = CodeBlock.builder().add("value = [")
            forEachIndexed { i, type ->
                if (i > 0) {
                    codeBlock.add(", ")
                }
                codeBlock.add("%T::class", type)
            }
            val value = codeBlock.add("]").build()
            return AnnotationSpec.builder(CommonClassNames.tag).addMember(value).build()
        }
    }

    fun Collection<String>.tagsMatch(other: Collection<String>): Boolean {
        if (this.isEmpty() && other.isEmpty()) {
            return true
        }
        if (this.isEmpty()) {
            return false
        }
        if (this.contains(CommonClassNames.tagAny.canonicalName)) {
            return true
        }

        for (tag in this) {
            if (tag !in other) {
                return false
            }
        }
        return true
    }
}
