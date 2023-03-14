package ru.tinkoff.kora.database.symbol.processor.model

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.toClassName
import ru.tinkoff.kora.database.symbol.processor.DbUtils
import ru.tinkoff.kora.database.symbol.processor.parseColumnName
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValue
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.MappersData
import ru.tinkoff.kora.ksp.common.getNameConverter
import ru.tinkoff.kora.ksp.common.parseMappingData

data class DbEntity(val type: KSType, val classDeclaration: KSClassDeclaration, val fields: List<EntityField>) {
    val columns = fields.asSequence().flatMap {
        when (it) {
            is SimpleEntityField -> {
                val propertyName = it.property.simpleName.asString()
                sequenceOf(Column(it.property, it.type, propertyName, propertyName, it.columnName, listOf(propertyName), it.type.isMarkedNullable, it.mapping))
            }

            is EmbeddedEntityField -> it.fields.asSequence().map { f ->
                val parentPropertyName = f.parent.property.simpleName.asString()
                val propertyName = f.property.simpleName.asString()
                Column(f.property, f.type, "$parentPropertyName.$propertyName", f.variableName, f.columnName, listOf(parentPropertyName, propertyName), f.type.isMarkedNullable || f.parent.type.isMarkedNullable, f.mapping)
            }
        }
    }.toList()

    data class Column(val property: KSPropertyDeclaration, val type: KSType, val sqlParameterName: String, val variableName: String, val columnName: String, val names: List<String>, val isNullable: Boolean, val mapping: MappersData) {
        fun queryParameterName(variableName: String): String {
            return "$variableName.$sqlParameterName"
        }

        fun accessor(nullSafe: Boolean): CodeBlock {
            val point = if (nullSafe) "?." else "."
            return when (names.size) {
                1 -> CodeBlock.of("%N", names[0])
                2 -> CodeBlock.of("%N%L%N", names[0], point, names[1])
                3 -> CodeBlock.of("%N%L%N%L%N", names[0], point, names[1], point, names[2])
                else -> throw IllegalArgumentException()
            }
        }
    }

    fun buildEmbeddedFields(): CodeBlock {
        val b = CodeBlock.builder()
        for (field in fields) {
            if (field !is EmbeddedEntityField) continue
            if (!field.parent.type.isMarkedNullable) {
                b.addStatement("val %N = %L", field.parent.property.simpleName.asString(), field.buildInstance())
                continue
            }
            b.controlFlow("val %N = when ", field.property.simpleName.asString()) {
                b.add(field.fields.map { CodeBlock.of("%N == null", it.variableName) }.joinToCode(" && ", "", " -> null\n"))
                for (column in field.fields) {
                    if (!column.type.isMarkedNullable) {
                        b.add("%N == null -> throw %T(%S)\n", column.variableName, NullPointerException::class.java, "Field ${column.property.simpleName.asString()} is not nullable, but column ${column.columnName} is null")
                    }
                }
                b.add("else -> %L", field.buildInstance())
            }
        }
        return b.build()
    }

    sealed interface EntityField {
        val property: KSPropertyDeclaration
        val type: KSType
        val mapping: MappersData
    }

    private class SimpleEntityField(override val property: KSPropertyDeclaration, override val type: KSType, val columnName: String, override val mapping: MappersData) : EntityField
    private class EmbeddedEntityField(val parent: SimpleEntityField, override val property: KSPropertyDeclaration, override val type: KSType, val fields: List<Field>) : EntityField {
        fun buildInstance(): CodeBlock {
            val b = CodeBlock.builder()
            b.add("%T(", type.toClassName()).indent().add("\n")
            for (i in fields.indices) {
                val field = fields[i]
                if (i > 0) {
                    b.add(",\n")
                }
                b.add("%N", field.variableName)
            }
            b.unindent().add(")\n")
            return b.build()
        }

        data class Field(val parent: SimpleEntityField, val property: KSPropertyDeclaration, val type: KSType, val columnName: String, val mapping: MappersData) {
            val variableName = parent.property.simpleName.asString() + "_" + property.simpleName.asString()
        }

        override val mapping: MappersData
            get() = TODO("Not yet implemented")
    }

    companion object {
        @OptIn(KspExperimental::class)
        fun parseEntity(type: KSType): DbEntity? {
            if (type.declaration !is KSClassDeclaration) {
                return null
            }
            val typeDeclaration = type.declaration as KSClassDeclaration
            if (!typeDeclaration.modifiers.contains(Modifier.DATA)) {
                return null
            }
            val nameConverter = typeDeclaration.getNameConverter()
            val property: (KSValueParameter) -> KSPropertyDeclaration = lambda@{ p ->
                for (property in typeDeclaration.getAllProperties()) {
                    if (property.simpleName.getShortName() == p.name!!.getShortName()) {
                        return@lambda property
                    }
                }
                throw IllegalStateException()
            }
            val fields = typeDeclaration.primaryConstructor!!.parameters
                .map {
                    val property = property(it)
                    val type = it.type.resolve()
                    val columnName = parseColumnName(it, nameConverter)
                    val field = SimpleEntityField(property, type, columnName, it.parseMappingData())
                    val embedded = it.findAnnotation(DbUtils.embeddedAnnotation)
                    if (embedded == null) {
                        return@map field
                    }
                    val prefix = embedded.findValue<String>("value")?.ifEmpty { null } ?: "${property.simpleName.asString()}_"
                    val entity = parseEntity(type)!!
                    val embeddedFields = entity.fields.map { f -> EmbeddedEntityField.Field(field, f.property, f.type, prefix + (f as SimpleEntityField).columnName, f.mapping) }
                    EmbeddedEntityField(field, property, type, embeddedFields)
                }
                .toList()
            return DbEntity(
                typeDeclaration.asStarProjectedType(),
                typeDeclaration,
                fields
            )
        }

    }
}
