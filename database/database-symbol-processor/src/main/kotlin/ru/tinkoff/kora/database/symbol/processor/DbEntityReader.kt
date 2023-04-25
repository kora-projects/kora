package ru.tinkoff.kora.database.symbol.processor

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.database.symbol.processor.model.DbEntity

class DbEntityReader(
    private val fieldMapperName: ClassName,
    private val mapperCallGenerator: (FieldData) -> CodeBlock,
    private val nativeTypeExtractGenerator: (FieldData) -> CodeBlock?,
    private val nullCheckGenerator: (FieldData) -> CodeBlock,
) {
    data class FieldData(val type: KSType, val mapperFieldName: String, val columnName: String, val fieldName: String, val isNullable: Boolean)
    data class ReadEntityCodeBlock(val block: CodeBlock, val requiredMappers: List<Mapper>) {
        fun enrich(type: TypeSpec.Builder, constructor: FunSpec.Builder) {
            DbUtils.addMappers(type, constructor, requiredMappers)
        }
    }

    fun readEntity(variableName: String, entity: DbEntity): ReadEntityCodeBlock {
        val b = CodeBlock.builder()
        val mappers = ArrayList<Mapper>()
        for (entityField in entity.columns) {

            val mapper = entityField.mapping.getMapping(this.fieldMapperName)
            val fieldName = entityField.variableName
            val mapperFieldName = "\$${fieldName}Mapper"
            val fieldData = FieldData(entityField.type, mapperFieldName, entityField.columnName, fieldName, entityField.isNullable)
            val mapperTypeParameter = entityField.type.toTypeName().copy(false)
            val fieldType = mapperTypeParameter.copy(true)
            if (mapper != null) {
                val mapperType = if (mapper.mapper != null)
                    mapper.mapper!!.toClassName()
                else
                    this.fieldMapperName.parameterizedBy(mapperTypeParameter)
                if (mapper.mapper != null && mapper.tags.isEmpty()) {
                    mappers.add(Mapper(mapper, mapperType, mapperFieldName))
                } else {
                    val tag = mapper.toTagAnnotation()
                    val param = ParameterSpec.builder(mapperFieldName, mapperType)
                    if (tag != null) {
                        param.addAnnotation(tag)
                    }
                    mappers.add(Mapper(mapperType, mapperFieldName))
                }
                b.add("var %N: %T = %L", fieldName, fieldType, mapperCallGenerator(fieldData))
            } else {
                val extractNative = this.nativeTypeExtractGenerator(fieldData)
                if (extractNative != null) {
                    b.add("var %N: %T = %L", fieldName, fieldType, extractNative)
                } else {
                    val mapperType = this.fieldMapperName.parameterizedBy(mapperTypeParameter)
                    mappers.add(Mapper(mapperType, mapperFieldName))
                    b.add("var %N: %T = %L", fieldName, fieldType, this.mapperCallGenerator(fieldData))
                }
            }
            b.add("\n")
            b.add(this.nullCheckGenerator(fieldData))
        }
        b.add(entity.buildEmbeddedFields())
        b.add("val %N = %T(", variableName, entity.type.toClassName())
        entity.fields.forEachIndexed { i, field ->
            if (i > 0) {
                b.add(", ")
            }
            b.add(field.property.simpleName.getShortName())
        }
        b.add(")\n")
        return ReadEntityCodeBlock(b.build(), mappers)
    }
}
