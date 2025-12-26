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
import ru.tinkoff.kora.ksp.common.TagUtils.addTag

class FieldFactory(builder: TypeSpec.Builder, constructor: FunSpec.Builder, prefix: String) {
    private val builder: TypeSpec.Builder
    private val constructor: FunSpec.Builder
    private val fields: MutableMap<FieldKey, String> = HashMap()
    private val prefix: String

    operator fun get(mapperType: TypeName, resultMapperTag: String?): String? {
        return fields[FieldKey(mapperType, resultMapperTag)]
    }

    operator fun get(mapperType: ClassName, mappedType: KSType, element: KSAnnotated): String {
        val type = mapperType.parameterizedBy(mappedType.toTypeName().copy(false))
        val tags = TagUtils.parseTagValue(element)
        val key = FieldKey(type, tags)
        return fields[key]!!
    }

    operator fun get(mapperClass: KSType, resultMapperTag: String?): String {
        val mapperType: TypeName = mapperClass.toTypeName()
        return fields[FieldKey(mapperType, resultMapperTag)]!!
    }

    internal data class FieldKey(val typeName: TypeName, val tag: String?)

    init {
        this.builder = builder
        this.constructor = constructor
        this.prefix = prefix
    }

    fun add(typeName: TypeName, tag: String?): String {
        val key = FieldKey(typeName, tag)
        val existed = fields[key]
        if (existed != null) {
            return existed
        }
        val name = prefix + (fields.size + 1)
        fields[key] = name
        builder.addProperty(name, typeName, KModifier.PRIVATE)
        val parameter = builder(name, typeName)
        parameter.addTag(tag)
        constructor.addParameter(parameter.build())
        constructor.addStatement("this.%N = %N", name, name)
        return name
    }

    fun add(typeName: TypeName, initializer: CodeBlock): String {
        val key = FieldKey(typeName, null)
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

    fun add(typeMirror: KSType, tag: String?): String {
        val typeName = typeMirror.toTypeName()
        val key = FieldKey(typeName, tag)
        val existed = fields[key]
        if (existed != null) {
            return existed
        }
        val name = prefix + (fields.size + 1)
        fields[key] = name
        builder.addProperty(name, typeName, KModifier.PRIVATE)
        val decl = typeMirror.declaration
        if (tag == null && decl is KSClassDeclaration && !decl.isOpen() && decl.getConstructors().count() == 1 && decl.getConstructors().first().parameters.isEmpty()) {
            constructor.addStatement("this.%N = %T()", name, typeName)
        } else {
            constructor.addParameter(name, typeName)
            constructor.addStatement("this.%N = %N", name, name)
        }
        return name
    }

    fun add(mapping: MappingData?, fieldParserType: TypeName): String {
        val typeName = mapping?.mapper?.toTypeName() ?: fieldParserType
        val tag = mapping?.tag
        val key = FieldKey(typeName, tag)
        val existed = fields[key]
        if (existed != null) {
            return existed
        }
        val name = prefix + (fields.size + 1)
        fields[key] = name
        builder.addProperty(name, typeName, KModifier.PRIVATE)
        val decl = mapping?.mapper?.declaration
        if (tag == null && decl is KSClassDeclaration && !decl.isOpen() && decl.getConstructors().count() == 1 && decl.getConstructors().first().parameters.isEmpty()) {
            constructor.addStatement("this.%N = %T()", name, typeName)
        } else {
            val parameter = builder(name, typeName)
                .addTag(tag)
            constructor.addParameter(parameter.build())
            constructor.addStatement("this.%N = %N", name, name)
        }
        return name
    }
}
