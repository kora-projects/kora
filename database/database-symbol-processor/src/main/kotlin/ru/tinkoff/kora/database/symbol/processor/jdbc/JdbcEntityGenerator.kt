package ru.tinkoff.kora.database.symbol.processor.jdbc

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.symbol.KSDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.database.symbol.processor.DbEntityReader
import ru.tinkoff.kora.database.symbol.processor.jdbc.extension.JdbcTypesExtension
import ru.tinkoff.kora.database.symbol.processor.model.DbEntity
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.KspCommonUtils.addOriginatingKSFile
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.generatedClass

class JdbcEntityGenerator(val codeGenerator: CodeGenerator) {
    private val entityReader = DbEntityReader(
        JdbcTypes.jdbcResultColumnMapper,
        { CodeBlock.of("%N.apply(_rs, _idx_%L)", it.mapperFieldName, it.fieldName) },
        { JdbcNativeTypes.findNativeType(it.type.toTypeName())?.extract("_rs", CodeBlock.of("_idx_%L", it.fieldName)) },
        {
            CodeBlock.builder().controlFlow("if (_rs.wasNull() || %N == null)", it.fieldName) {
                if (it.isNullable) {
                    addStatement("%N = null", it.fieldName)
                } else {
                    addStatement("throw %T(%S)", NullPointerException::class.asClassName(), "Required field ${it.columnName} is not nullable but row has null")
                }
            }
                .build()
        }
    )

    companion object {
        fun KSDeclaration.listResultSetMapperName() = ClassName(packageName.asString(), generatedClass("ListJdbcResultSetMapper"))
        fun KSDeclaration.resultSetMapperName() = ClassName(packageName.asString(), generatedClass(JdbcTypes.jdbcResultSetMapper))
        fun KSDeclaration.rowMapperName() = ClassName(packageName.asString(), generatedClass(JdbcTypes.jdbcRowMapper))
    }


    fun generateListResultSetMapper(entity: DbEntity) {
        val mapperName = entity.type.declaration.listResultSetMapperName()
        val entityTypeName = entity.type.toTypeName().copy(false)
        val resultTypeName = List::class.asClassName().parameterizedBy(entityTypeName)
        val type = TypeSpec.classBuilder(mapperName)
            .addOriginatingKSFile(entity.classDeclaration)
            .generated(JdbcTypesExtension::class)
            .addSuperinterface(JdbcTypes.jdbcResultSetMapper.parameterizedBy(resultTypeName))

        val constructor = FunSpec.constructorBuilder()
        val apply = FunSpec.builder("apply")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("_rs", JdbcTypes.resultSet)
            .returns(resultTypeName)
        apply.controlFlow("if (!_rs.next())") {
            addStatement("return listOf()")
        }
        val read = this.entityReader.readEntity("_row", entity)
        read.enrich(type, constructor)
        apply.addCode(parseIndexes(entity, "_rs"))
        apply.addStatement("val _result = ArrayList<%T>()", entityTypeName)
        apply.addCode(
            CodeBlock.builder()
                .add("do {").indent().add("\n")
                .add(read.block)
                .add("_result.add(_row)")
                .unindent().add("\n} while(_rs.next())\n")
                .build()
        )
        apply.addStatement("return _result")


        type.primaryConstructor(constructor.build())
        type.addFunction(apply.build())

        FileSpec.get(mapperName.packageName, type.build()).writeTo(codeGenerator, false, listOfNotNull(entity.type.declaration.containingFile))
    }

    fun generateResultSetMapper(entity: DbEntity) {
        val mapperName = entity.type.declaration.resultSetMapperName()
        val entityTypeName = entity.type.toTypeName().copy(false)
        val type = TypeSpec.classBuilder(mapperName)
            .addOriginatingKSFile(entity.classDeclaration)
            .generated(JdbcTypesExtension::class)
            .addSuperinterface(JdbcTypes.jdbcResultSetMapper.parameterizedBy(entityTypeName))

        val constructor = FunSpec.constructorBuilder()
        val apply = FunSpec.builder("apply")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("_rs", JdbcTypes.resultSet)
            .returns(entityTypeName.copy(true))

        apply.controlFlow("if (!_rs.next())") {
            addStatement("return null")
        }

        val read = this.entityReader.readEntity("_result", entity)
        read.enrich(type, constructor)
        apply.addCode(parseIndexes(entity, "_rs"))
        apply.addCode(read.block)
        apply.controlFlow("if (_rs.next())") {
            apply.addStatement("throw IllegalStateException(%S)", "ResultSet was expected to return zero or one row but got two or more")
        }
        apply.addStatement("return _result")


        type.primaryConstructor(constructor.build())
        type.addFunction(apply.build())

        FileSpec.get(mapperName.packageName, type.build()).writeTo(codeGenerator, false, listOfNotNull(entity.type.declaration.containingFile))
    }

    fun generateRowMapper(entity: DbEntity) {
        val mapperName = entity.type.declaration.rowMapperName()
        val entityTypeName = entity.type.toTypeName()
        val type = TypeSpec.classBuilder(mapperName)
            .addOriginatingKSFile(entity.classDeclaration)
            .generated(JdbcTypesExtension::class)
            .addSuperinterface(JdbcTypes.jdbcRowMapper.parameterizedBy(entityTypeName))

        val constructor = FunSpec.constructorBuilder()
        val apply = FunSpec.builder("apply")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("_rs", JdbcTypes.resultSet)
            .returns(entityTypeName)

        val read = this.entityReader.readEntity("_result", entity)
        read.enrich(type, constructor)
        if (entity.type.isMarkedNullable) {
            apply.controlFlow("if (!_rs.next())") {
                addStatement("return null")
            }
        }
        apply.addCode(parseIndexes(entity, "_rs"))
        apply.addCode(read.block)
        apply.addStatement("return _result")


        type.primaryConstructor(constructor.build())
        type.addFunction(apply.build())

        FileSpec.get(mapperName.packageName, type.build()).writeTo(codeGenerator, false, listOfNotNull(entity.type.declaration.containingFile))
    }

    private fun parseIndexes(entity: DbEntity, rsName: String): CodeBlock {
        val cb = CodeBlock.builder()
        for (field in entity.columns) {
            cb.add("val _idx_%L = %N.findColumn(%S);\n", field.variableName, rsName, field.columnName)
        }
        return cb.build()
    }
}
