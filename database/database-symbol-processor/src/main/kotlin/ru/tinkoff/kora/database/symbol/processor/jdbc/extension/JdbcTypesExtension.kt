package ru.tinkoff.kora.database.symbol.processor.jdbc.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.database.symbol.processor.DbEntityReader
import ru.tinkoff.kora.database.symbol.processor.jdbc.JdbcNativeTypes
import ru.tinkoff.kora.database.symbol.processor.jdbc.JdbcTypes
import ru.tinkoff.kora.database.symbol.processor.model.DbEntity
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension
import ru.tinkoff.kora.ksp.common.CommonClassNames.isList
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.getOuterClassesAsPrefix
import javax.annotation.processing.Generated

// JdbcRowMapper<T>
// JdbcResultSetMapper<List<T>>
class JdbcTypesExtension(val resolver: Resolver, val kspLogger: KSPLogger, val codeGenerator: CodeGenerator) : KoraExtension {
    private val entityReader: DbEntityReader = DbEntityReader(
        JdbcTypes.jdbcResultColumnMapper,
        { CodeBlock.of("%N.apply(_rs, _idx_%L)", it.mapperFieldName, it.fieldName) },
        { JdbcNativeTypes.findNativeType(it.type.toTypeName())?.extract("_rs", CodeBlock.of("_idx_%L", it.fieldName)) },
        {
            CodeBlock.of(
                "if (_rs.wasNull() || %N == null) {\n  throw %T(%S);\n}\n",
                it.fieldName,
                NullPointerException::class.asClassName(),
                "Required field ${it.columnName} is not nullable but row has null"
            )
        }
    )

    override fun getDependencyGenerator(resolver: Resolver, type: KSType): (() -> ExtensionResult)? {
        if (type.declaration.qualifiedName == null) {
            return null
        }
        if (type.declaration.qualifiedName!!.asString() == JdbcTypes.jdbcRowMapper.canonicalName) {
            val rowType = type.arguments[0].type!!.resolve()
            val entity = DbEntity.parseEntity(rowType)
            if (entity != null) {
                return this.entityRowMapper(resolver, entity)
            } else {
                return null
            }
        }
        if (type.declaration.qualifiedName!!.asString() == JdbcTypes.jdbcResultSetMapper.canonicalName) {
            val resultType = type.arguments[0].type!!.resolve()
            if (resultType.isList()) {
                val rowType = resultType.arguments[0].type!!.resolve()
                val entity = DbEntity.parseEntity(rowType)
                if (entity != null) {
                    return this.entityListResultSetMapper(resolver, entity)
                } else {
                    return null
                }
            }
        }
        return null
    }

    private fun entityListResultSetMapper(resolver: Resolver, entity: DbEntity): (() -> ExtensionResult)? {
        val mapperName = entity.type.declaration.getOuterClassesAsPrefix() + "${entity.type.declaration.simpleName.getShortName()}_JdbcListResultSetMapper"
        val packageName = entity.type.declaration.packageName.asString()
        return lambda@{
            val maybeGenerated = resolver.getClassDeclarationByName("$packageName.$mapperName")
            if (maybeGenerated != null) {
                val constructor = maybeGenerated.primaryConstructor
                if (constructor == null) {
                    throw IllegalStateException()
                }
                return@lambda ExtensionResult.fromConstructor(constructor, maybeGenerated)
            }
            val entityTypeName = entity.type.toTypeName()
            val resultTypeName = List::class.asClassName().parameterizedBy(entityTypeName)
            val type = TypeSpec.classBuilder(mapperName)
                .addAnnotation(AnnotationSpec.builder(Generated::class).addMember("value = [%S]", JdbcTypesExtension::class.qualifiedName!!).build())
                .addSuperinterface(JdbcTypes.jdbcResultSetMapper.parameterizedBy(resultTypeName))

            val constructor = FunSpec.constructorBuilder()
            val apply = FunSpec.builder("apply")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("_rs", JdbcTypes.resultSet)
                .returns(resultTypeName)

            val read = this.entityReader.readEntity("_row", entity)
            read.enrich(type, constructor)
            apply.addCode(parseIndexes(entity, "_rs"))
            apply.addStatement("val _result = ArrayList<%T>()", entityTypeName)
            apply.controlFlow("while (_rs.next())") {
                addCode(read.block)
                addStatement("_result.add(_row)")
            }
            apply.addStatement("return _result")


            type.primaryConstructor(constructor.build())
            type.addFunction(apply.build())

            FileSpec.get(packageName, type.build()).writeTo(codeGenerator, true, listOfNotNull(entity.type.declaration.containingFile))

            ExtensionResult.RequiresCompilingResult
        }
    }

    private fun entityRowMapper(resolver: Resolver, entity: DbEntity): (() -> ExtensionResult) {
        val mapperName = entity.type.declaration.getOuterClassesAsPrefix() + "${entity.type.declaration.simpleName.getShortName()}_JdbcRowMapper"
        val packageName = entity.type.declaration.packageName.asString()
        return lambda@{
            val maybeGenerated = resolver.getClassDeclarationByName("$packageName.$mapperName")
            if (maybeGenerated != null) {
                val constructor = maybeGenerated.primaryConstructor
                if (constructor == null) {
                    throw IllegalStateException()
                }
                return@lambda ExtensionResult.fromConstructor(constructor, maybeGenerated)
            }
            val entityTypeName = entity.type.toTypeName()
            val type = TypeSpec.classBuilder(mapperName)
                .addAnnotation(AnnotationSpec.builder(Generated::class).addMember("value = [%S]", JdbcTypesExtension::class.qualifiedName!!).build())
                .addSuperinterface(JdbcTypes.jdbcRowMapper.parameterizedBy(entityTypeName))

            val constructor = FunSpec.constructorBuilder()
            val apply = FunSpec.builder("apply")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("_rs", JdbcTypes.resultSet)
                .returns(entityTypeName)

            val read = this.entityReader.readEntity("_result", entity)
            read.enrich(type, constructor)
            apply.addCode(parseIndexes(entity, "_rs"))
            apply.addCode(read.block)
            apply.addStatement("return _result")


            type.primaryConstructor(constructor.build())
            type.addFunction(apply.build())

            FileSpec.get(packageName, type.build()).writeTo(codeGenerator, true, listOfNotNull(entity.type.declaration.containingFile))

            ExtensionResult.RequiresCompilingResult
        }
    }

    private fun parseIndexes(entity: DbEntity, rsName: String): CodeBlock {
        val cb = CodeBlock.builder()
        for (field in entity.fields) {
            cb.add("val _idx_%L = %N.findColumn(%S);\n", field.property.simpleName.getShortName(), rsName, field.columnName)
        }
        return cb.build()
    }

}
