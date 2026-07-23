package io.koraframework.database.symbol.processor.jdbc

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.symbol.KSDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import io.koraframework.database.symbol.processor.DbEntityReader
import io.koraframework.database.symbol.processor.jdbc.extension.JdbcTypesExtension
import io.koraframework.database.symbol.processor.model.DbEntity
import io.koraframework.ksp.common.KotlinPoetUtils.controlFlow
import io.koraframework.ksp.common.KspCommonUtils.addOriginatingKSFile
import io.koraframework.ksp.common.KspCommonUtils.generated
import io.koraframework.ksp.common.generatedClass
import io.koraframework.ksp.common.exception.ProcessingErrorException

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


    fun generateListResultSetMapper(entity: DbEntity, aggregating: Boolean) {
        if (entity.hasEmbeddedCollection) {
            generateAggregatingListResultSetMapper(entity, aggregating)
            return
        }

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

        FileSpec.get(mapperName.packageName, type.build()).writeTo(codeGenerator, aggregating, listOfNotNull(entity.type.declaration.containingFile))
    }

    private fun generateAggregatingListResultSetMapper(entity: DbEntity, aggregating: Boolean) {
        val collections = entity.embeddedCollections
        if (collections.size != 1) {
            throw ProcessingErrorException("Only one @Embedded collection field is supported in JdbcResultSetMapper: ${entity.classDeclaration}", collections[1].property)
        }
        val rootIdColumns = entity.rootIdColumns
        if (rootIdColumns.isEmpty()) {
            throw ProcessingErrorException(
                "@Id field is required for one-to-many JdbcResultSetMapper root. Entity: ${entity.classDeclaration}; " +
                    "root fields: ${entity.rootFieldsDescription}; " +
                    "add @Id to one of root fields or to a field inside embedded root type, not to @Embedded collection element only.",
                entity.rootErrorElement
            )
        }
        val collection = collections[0]
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
        apply.addStatement("val _index = LinkedHashMap<List<Any?>, %T>()", entityTypeName)
        apply.addCode(
            CodeBlock.builder()
                .add("do {").indent().add("\n")
                .add(read.block)
                .add("val _key = listOf<Any?>(")
                .add(rootIdColumns.map { CodeBlock.of("%N", it.variableName) }.joinToCode(", "))
                .add(")\n")
                .add("val _existing = _index[_key]\n")
                .add("if (_existing == null) {").indent().add("\n")
                .add("_index[_key] = _row\n")
                .add("_result.add(_row)\n")
                .unindent().add("} else {").indent().add("\n")
                .add("(_existing.%N as MutableList<%T>).addAll(_row.%N)\n", collection.parent.property.simpleName.asString(), collection.elementType.toTypeName().copy(false), collection.parent.property.simpleName.asString())
                .unindent().add("}\n")
                .unindent().add("} while(_rs.next())\n")
                .build()
        )
        apply.addStatement("return _result")

        type.primaryConstructor(constructor.build())
        type.addFunction(apply.build())

        FileSpec.get(mapperName.packageName, type.build()).writeTo(codeGenerator, aggregating, listOfNotNull(entity.type.declaration.containingFile))
    }

    fun generateResultSetMapper(entity: DbEntity, aggregating: Boolean) {
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

        FileSpec.get(mapperName.packageName, type.build()).writeTo(codeGenerator, aggregating, listOfNotNull(entity.type.declaration.containingFile))
    }

    fun generateRowMapper(entity: DbEntity, aggregating: Boolean) {
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

        FileSpec.get(mapperName.packageName, type.build()).writeTo(codeGenerator, aggregating, listOfNotNull(entity.type.declaration.containingFile))
    }

    private fun parseIndexes(entity: DbEntity, rsName: String): CodeBlock {
        val cb = CodeBlock.builder()
        for (field in entity.columns) {
            cb.add("val _idx_%L = %N.findColumn(%S);\n", field.variableName, rsName, field.columnName)
        }
        return cb.build()
    }
}
