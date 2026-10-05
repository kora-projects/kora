package io.koraframework.database.symbol.processor.jdbc

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.symbol.KSDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import io.koraframework.database.symbol.processor.DbEntityReader
import io.koraframework.database.symbol.processor.model.DbEntity
import io.koraframework.ksp.common.KotlinPoetUtils.controlFlow
import io.koraframework.ksp.common.KspCommonUtils.addOriginatingKSFile
import io.koraframework.ksp.common.KspCommonUtils.generated
import io.koraframework.ksp.common.generatedClassName
import io.koraframework.ksp.common.generatedHolder
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
        const val HOLDER_POSTFIX = "Jdbc"
        const val ROW_MAPPER_NAME = "RowMapper"
        const val RESULT_SET_MAPPER_NAME = "ResultSetMapper"
        const val LIST_RESULT_SET_MAPPER_NAME = "ListResultSetMapper"
    }

    /**
     * All mappers of the entity are written as nested classes of a single holder, e.g. `$Entity_Jdbc.RowMapper`
     */
    fun generate(entity: DbEntity) {
        val holder = generatedHolder(entity.classDeclaration.generatedClassName(HOLDER_POSTFIX), JdbcEntitySymbolProcessor::class)
            .addOriginatingKSFile(entity.classDeclaration)
            .addType(generateRowMapper(entity))
            .addType(generateResultSetMapper(entity))
            .addType(generateListResultSetMapper(entity))
            .build()

        FileSpec.get(entity.classDeclaration.packageName.asString(), holder).writeTo(codeGenerator, false, listOfNotNull(entity.classDeclaration.containingFile))
    }

    private fun generateListResultSetMapper(entity: DbEntity): TypeSpec {
        if (entity.hasEmbeddedCollection) {
            return generateAggregatingListResultSetMapper(entity)
        }

        val entityTypeName = entity.type.toTypeName().copy(false)
        val resultTypeName = List::class.asClassName().parameterizedBy(entityTypeName)
        val type = TypeSpec.classBuilder(LIST_RESULT_SET_MAPPER_NAME)
            .addOriginatingKSFile(entity.classDeclaration)
            .generated(JdbcEntitySymbolProcessor::class)
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

        return type.build()
    }

    private fun generateAggregatingListResultSetMapper(entity: DbEntity): TypeSpec {
        val collections = entity.embeddedCollections
        if (collections.size != 1) {
            val errorElement = collections.getOrNull(1)?.property ?: entity.rootErrorElement
            throw ProcessingErrorException(embeddedCollectionCountError(entity, collections.size), errorElement)
        }
        val rootIdColumns = entity.rootIdColumns
        if (rootIdColumns.isEmpty()) {
            throw ProcessingErrorException(missingRootIdError(entity), entity.rootErrorElement)
        }
        val collection = collections[0]
        val entityTypeName = entity.type.toTypeName().copy(false)
        val resultTypeName = List::class.asClassName().parameterizedBy(entityTypeName)
        val type = TypeSpec.classBuilder(LIST_RESULT_SET_MAPPER_NAME)
            .addOriginatingKSFile(entity.classDeclaration)
            .generated(JdbcEntitySymbolProcessor::class)
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

        return type.build()
    }

    private fun generateResultSetMapper(entity: DbEntity): TypeSpec {
        val entityTypeName = entity.type.toTypeName().copy(false)
        val type = TypeSpec.classBuilder(RESULT_SET_MAPPER_NAME)
            .addOriginatingKSFile(entity.classDeclaration)
            .generated(JdbcEntitySymbolProcessor::class)
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

        return type.build()
    }

    private fun generateRowMapper(entity: DbEntity): TypeSpec {
        val entityTypeName = entity.type.toTypeName()
        val type = TypeSpec.classBuilder(ROW_MAPPER_NAME)
            .addOriginatingKSFile(entity.classDeclaration)
            .generated(JdbcEntitySymbolProcessor::class)
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

        return type.build()
    }

    private fun parseIndexes(entity: DbEntity, rsName: String): CodeBlock {
        val cb = CodeBlock.builder()
        for (field in entity.columns) {
            cb.add("val _idx_%L = %N.findColumn(%S);\n", field.variableName, rsName, field.columnName)
        }
        return cb.build()
    }

    private fun embeddedCollectionCountError(entity: DbEntity, collectionCount: Int): String {
        return """
            Invalid JDBC one-to-many entity mapper for `${entity.classDeclaration.qualifiedName?.asString()}`.

            `JdbcResultSetMapper<List<${entity.classDeclaration.simpleName.asString()}>>` supports exactly one `@Embedded` collection field, but found $collectionCount.
            Multiple collection joins cannot be safely aggregated into a single result shape.

            Fix: keep one `@Embedded` collection in this entity, split the query into separate repository methods, or map the result manually with a custom mapper.
        """.trimIndent()
    }

    private fun missingRootIdError(entity: DbEntity): String {
        return """
            Invalid JDBC one-to-many entity mapper for `${entity.classDeclaration.qualifiedName?.asString()}`.

            The root entity must have an `@Id` column outside the embedded collection.
            Root fields: ${entity.rootFieldsDescription}

            Fix: add `@Id` to one of the root fields, or to a field inside an embedded root object.
            Do not put the only `@Id` on the `@Embedded` collection element.
        """.trimIndent()
    }
}
