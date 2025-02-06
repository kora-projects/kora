package ru.tinkoff.kora.database.symbol.processor.cassandra

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.database.symbol.processor.DbEntityReader
import ru.tinkoff.kora.database.symbol.processor.cassandra.extension.CassandraTypesExtension
import ru.tinkoff.kora.database.symbol.processor.model.DbEntity
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.generatedClass

class CassandraEntityGenerator(val codeGenerator: CodeGenerator) {
    private val entityReader = DbEntityReader(
        CassandraTypes.rowColumnMapper,
        { CodeBlock.of("%N.apply(_row, _idx_%L)", it.mapperFieldName, it.fieldName) },
        { CassandraNativeTypes.findNativeType(it.type.toTypeName())?.extract("_row", CodeBlock.of("_idx_%L", it.fieldName)) },
        {
            CodeBlock.builder().controlFlow("if (_row.isNull(%N) || %N == null)", "_idx_${it.fieldName}", it.fieldName) {
                if (it.isNullable) {
                    addStatement("%N = null", it.fieldName)
                } else {
                    addStatement("throw %T(%S)", NullPointerException::class.asClassName(), "Required field ${it.columnName} is not nullable but row has null")
                }
            }
                .build()
        }
    )

    fun generateResultSetMapper(entity: DbEntity) {
        val rowType = entity.type
        val mapperName = rowType.resultSetMapperClassName()
        val packageName = rowType.declaration.packageName.asString()
        val entityTypeName = entity.type.toTypeName();
        val type = TypeSpec.classBuilder(mapperName)
            .generated(CassandraTypesExtension::class)
            .addSuperinterface(CassandraTypes.resultSetMapper.parameterizedBy(entityTypeName))

        val constructor = FunSpec.constructorBuilder()
        val apply = FunSpec.builder("apply")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("_rs", CassandraTypes.resultSet)
            .returns(entityTypeName.copy(nullable = true))
        apply.addStatement("val _it = _rs.iterator()")
        apply.controlFlow("if (!_it.hasNext())") {
            apply.addStatement("return null")
        }
        apply.addStatement("val _row = _it.next()")
        apply.addCode(parseIndexes(entity, "_rs"))
        val read = this.entityReader.readEntity("_result", entity)
        read.enrich(type, constructor)
        apply.addCode(read.block)
        // todo throw exception on more than one row
        apply.addStatement("return _result")


        type.primaryConstructor(constructor.build())
        type.addFunction(apply.build())

        FileSpec.get(packageName, type.build()).writeTo(codeGenerator, true, listOfNotNull(entity.classDeclaration.containingFile))
    }

    fun generateListResultSetMapper(entity: DbEntity) {
        val rowType = entity.type
        val mapperName = rowType.listResultSetMapperClassName()
        val packageName = rowType.declaration.packageName.asString()
        val entityTypeName = entity.type.toTypeName();
        val listType = List::class.asClassName().parameterizedBy(entityTypeName)
        val type = TypeSpec.classBuilder(mapperName)
            .generated(CassandraTypesExtension::class)
            .addSuperinterface(CassandraTypes.resultSetMapper.parameterizedBy(listType))

        val constructor = FunSpec.constructorBuilder()
        val apply = FunSpec.builder("apply")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("_rs", CassandraTypes.resultSet)
            .returns(listType)
        apply.addCode(parseIndexes(entity, "_rs"))
        apply.addCode("val _result = %T<%T>(_rs.availableWithoutFetching);\n", ArrayList::class, entityTypeName)
        val read = this.entityReader.readEntity("_mappedRow", entity)
        read.enrich(type, constructor)
        apply.beginControlFlow("for (_row in _rs)")
        apply.addCode(read.block)
        apply.addCode("_result.add(_mappedRow)\n")
        apply.endControlFlow()

        apply.addCode("return _result;\n")


        type.primaryConstructor(constructor.build())
        type.addFunction(apply.build())

        FileSpec.get(packageName, type.build()).writeTo(codeGenerator, true, listOfNotNull(entity.classDeclaration.containingFile))
    }

    fun generateRowMapper(entity: DbEntity) {
        val mapperName = entity.type.rowMapperClassName()
        val type = TypeSpec.classBuilder(mapperName)
            .generated(CassandraTypesExtension::class)
            .addSuperinterface(CassandraTypes.rowMapper.parameterizedBy(entity.type.toTypeName()))

        val constructor = FunSpec.constructorBuilder()
        val apply = FunSpec.builder("apply")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("_row", CassandraTypes.row)
            .returns(entity.type.toTypeName())

        val read = this.entityReader.readEntity("_result", entity)
        read.enrich(type, constructor)
        apply.addCode(parseIndexes(entity, "_row"))
        apply.addCode(read.block)
        apply.addCode("return _result;\n")


        type.primaryConstructor(constructor.build())
        type.addFunction(apply.build())

        FileSpec.get(entity.classDeclaration.packageName.asString(), type.build()).writeTo(codeGenerator, true, listOfNotNull(entity.classDeclaration.containingFile))
    }

    private fun parseIndexes(entity: DbEntity, rsName: String): CodeBlock {
        val cb = CodeBlock.builder()
        for (field in entity.columns) {
            cb.add("val %N = %N.columnDefinitions.firstIndexOf(%S)\n", "_idx_${field.variableName}", rsName, field.columnName)
        }
        return cb.build()
    }

    companion object {
        fun KSType.rowMapperClassName() = ClassName(this.declaration.packageName.asString(), this.declaration.generatedClass(CassandraTypes.rowMapper))
        fun KSType.resultSetMapperClassName() = ClassName(this.declaration.packageName.asString(), this.declaration.generatedClass(CassandraTypes.resultSetMapper))
        fun KSType.listResultSetMapperClassName() = ClassName(this.declaration.packageName.asString(), this.declaration.generatedClass("ListCassandraResultSetMapper"))
    }
}
