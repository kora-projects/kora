package ru.tinkoff.kora.database.symbol.processor.jdbc.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getFunctionDeclarationsByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Variance
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
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.KspCommonUtils.parametrized
import ru.tinkoff.kora.ksp.common.getOuterClassesAsPrefix

// JdbcRowMapper<T>
// JdbcResultSetMapper<List<T>>
class JdbcTypesExtension(val resolver: Resolver, val kspLogger: KSPLogger, val codeGenerator: CodeGenerator) : KoraExtension {
    private val entityReader: DbEntityReader = DbEntityReader(
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
            } else {
                return {
                    val resultSetMapperDecl = resolver.getClassDeclarationByName(JdbcTypes.jdbcResultSetMapper.canonicalName)!!
                    val rowMapperDecl = resolver.getClassDeclarationByName(JdbcTypes.jdbcRowMapper.canonicalName)!!

                    val resultSetMapperType = resultSetMapperDecl.asType(listOf(resolver.getTypeArgument(
                        resolver.createKSTypeReferenceFromKSType(resultType), Variance.INVARIANT
                    )))
                    val rowMapperType = rowMapperDecl.asType(listOf(resolver.getTypeArgument(
                        resolver.createKSTypeReferenceFromKSType(resultType), Variance.INVARIANT
                    )))

                    val functionDecl = resolver.getFunctionDeclarationsByName(JdbcTypes.jdbcResultSetMapper.canonicalName + ".singleResultSetMapper").first()
                    val functionType = functionDecl.parametrized(resultSetMapperType, listOf(rowMapperType))
                    ExtensionResult.fromExecutable(functionDecl, functionType)
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
            apply.addCode(CodeBlock.builder()
                .add("do {").indent().add("\n")
                .add(read.block)
                .add("_result.add(_row)")
                .unindent().add("\n} while(_rs.next())\n")
                .build())
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
                .generated(JdbcTypesExtension::class)
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
        for (field in entity.columns) {
            cb.add("val _idx_%L = %N.findColumn(%S);\n", field.variableName, rsName, field.columnName)
        }
        return cb.build()
    }

}
