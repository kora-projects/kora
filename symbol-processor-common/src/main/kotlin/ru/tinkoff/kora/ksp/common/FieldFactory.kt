package ru.tinkoff.kora.ksp.common

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isOpen
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterSpec.Companion.builder
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName

class FieldFactory(builder: TypeSpec.Builder, constructor: FunSpec.Builder, prefix: String) {
    private val builder: TypeSpec.Builder
    private val constructor: FunSpec.Builder
    private val fields: MutableMap<FieldKey, String> = HashMap()
    private val prefix: String
    operator fun get(mapperType: TypeName, resultMapperTag: Set<String>): String? {
        return fields[FieldKey(mapperType, resultMapperTag)]
    }

    operator fun get(mapperType: ClassName, mappedType: KSType, element: KSAnnotated): String {
        val type = mapperType.parameterizedBy(mappedType.toTypeName().copy(false))
        val tags = TagUtils.parseTagValue(element)
        val key = FieldKey(type, tags)
        return fields[key]!!
    }

    operator fun get(mapperClass: KSType, resultMapperTag: Set<String>): String {
        val mapperType: TypeName = mapperClass.toTypeName()
        return fields[FieldKey(mapperType, resultMapperTag)]!!
    }

    internal data class FieldKey(val typeName: TypeName, val tags: Set<String>)

    init {
        this.builder = builder
        this.constructor = constructor
        this.prefix = prefix
    }

    fun add(typeName: TypeName, tags: Set<String>): String {
        val key = FieldKey(typeName, tags)
        val existed = fields[key]
        if (existed != null) {
            return existed
        }
        val name = prefix + (fields.size + 1)
        fields[key] = name
        builder.addProperty(name, typeName, KModifier.PRIVATE)
        val parameter = builder(name, typeName)

        if (tags.isNotEmpty()) {
            parameter.addAnnotation(AnnotationSpec.builder(CommonClassNames.tag).addMember("value = {%L}", tags.joinToString(", ") { "$it::class" }).build())
        }
        constructor.addParameter(parameter.build())
        constructor.addStatement("this.%N = %N", name, name)
        return name
    }

    fun add(typeName: TypeName, initializer: CodeBlock): String {
        val key = FieldKey(typeName, setOf())
        val existed = fields[key]
        if (existed != null) {
            return existed
        }
        val name = prefix + (fields.size + 1)
        fields[key] = name
        builder.addProperty(name, typeName, KModifier.PRIVATE)
        constructor.addStatement("this.%N = %L", name, initializer)
        return name
    }

    fun add(typeMirror: KSType, tags: Set<String>): String {
        val typeName = typeMirror.toTypeName()
        val key = FieldKey(typeName, tags)
        val existed = fields[key]
        if (existed != null) {
            return existed
        }
        val name = prefix + (fields.size + 1)
        fields[key] = name
        builder.addProperty(name, typeName, KModifier.PRIVATE)
        val decl = typeMirror.declaration
        if (tags.isEmpty() && decl is KSClassDeclaration && !decl.isOpen() && decl.getConstructors().count() == 1 && decl.getConstructors().first().parameters.isEmpty()) {
            constructor.addStatement("this.%N = %T()", name, typeName)
        } else {
            constructor.addParameter(name, typeName)
            constructor.addStatement("this.%N = %N", name, name)
        }
        return name
    }
}
