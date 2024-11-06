package ru.tinkoff.kora.database.symbol.processor.jdbc.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getFunctionDeclarationsByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Variance
import com.squareup.kotlinpoet.ClassName
import ru.tinkoff.kora.database.symbol.processor.jdbc.JdbcEntityGenerator
import ru.tinkoff.kora.database.symbol.processor.jdbc.JdbcEntityGenerator.Companion.listResultSetMapperName
import ru.tinkoff.kora.database.symbol.processor.jdbc.JdbcEntityGenerator.Companion.resultSetMapperName
import ru.tinkoff.kora.database.symbol.processor.jdbc.JdbcEntityGenerator.Companion.rowMapperName
import ru.tinkoff.kora.database.symbol.processor.jdbc.JdbcTypes
import ru.tinkoff.kora.database.symbol.processor.model.DbEntity
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.CommonClassNames.isList
import ru.tinkoff.kora.ksp.common.KspCommonUtils.getClassDeclarationByName
import ru.tinkoff.kora.ksp.common.KspCommonUtils.parametrized

// JdbcRowMapper<T>
// JdbcResultSetMapper<T>
// JdbcResultSetMapper<List<T>>
class JdbcTypesExtension(val resolver: Resolver, val kspLogger: KSPLogger, val codeGenerator: CodeGenerator) : KoraExtension {
    private val generator = JdbcEntityGenerator(codeGenerator)

    private fun fromProcessor(resolver: Resolver, mapper: ClassName): (() -> ExtensionResult) {
        val generated = resolver.getClassDeclarationByName(mapper)
        if (generated != null) {
            return {
                val constructor = generated.primaryConstructor!!
                ExtensionResult.fromConstructor(constructor, generated)
            }
        } else {
            return { ExtensionResult.RequiresCompilingResult }
        }
    }

    private fun fromExtension(resolver: Resolver, mapperType: KSType, mapper: ClassName, generator: () -> Unit): (() -> ExtensionResult) {
        val generated = resolver.getClassDeclarationByName(mapper)
        if (generated != null) {
            return {
                val constructor = generated.primaryConstructor!!
                ExtensionResult.fromConstructor(constructor, generated)
            }
        } else {
            return {
                kspLogger.warn("Type is not annotated with @EntityJdbc, but mapper $mapperType is requested by graph. Generating one in graph building process will lead to another round of compiling which will slow down you build")
                generator()
                ExtensionResult.RequiresCompilingResult
            }
        }
    }

    override fun getDependencyGenerator(resolver: Resolver, type: KSType, tags: Set<String>): (() -> ExtensionResult)? {
        if (tags.isNotEmpty()) return null
        if (type.declaration.qualifiedName == null) {
            return null
        }
        if (type.declaration.qualifiedName!!.asString() == JdbcTypes.jdbcRowMapper.canonicalName) {
            val rowType = type.arguments[0].type!!.resolve()
            if (rowType.declaration.isAnnotationPresent(JdbcTypes.jdbcEntity)) {
                return fromProcessor(resolver, rowType.declaration.rowMapperName())
            }
            return DbEntity.parseEntity(rowType)?.let { entity ->
                fromExtension(resolver, type, rowType.declaration.rowMapperName()) {
                    generator.generateRowMapper(entity)
                }
            }
        }
        if (type.declaration.qualifiedName!!.asString() == JdbcTypes.jdbcResultSetMapper.canonicalName) {
            val resultType = type.arguments[0].type!!.resolve()
            if (resultType.isList()) {
                val rowType = resultType.arguments[0].type!!.resolve()
                if (rowType.declaration.isAnnotationPresent(JdbcTypes.jdbcEntity)) {
                    return fromProcessor(resolver, rowType.declaration.listResultSetMapperName())
                }

                val entity = DbEntity.parseEntity(rowType)
                if (entity != null) {
                    return fromExtension(resolver, type, rowType.declaration.listResultSetMapperName()) {
                        generator.generateListResultSetMapper(entity)
                    }
                } else {
                    val resultSetMapperDecl = resolver.getClassDeclarationByName(JdbcTypes.jdbcResultSetMapper.canonicalName)!!
                    val rowMapperDecl = resolver.getClassDeclarationByName(JdbcTypes.jdbcRowMapper.canonicalName)!!

                    val resultSetMapperType = resultSetMapperDecl.asType(
                        listOf(
                            resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(resultType), Variance.INVARIANT)
                        )
                    )
                    val rowMapperType = rowMapperDecl.asType(
                        listOf(
                            resolver.getTypeArgument(resultType.arguments[0].type!!, Variance.INVARIANT)
                        )
                    )

                    val functionDecl = resolver.getFunctionDeclarationsByName(JdbcTypes.jdbcResultSetMapper.canonicalName + ".listResultSetMapper").first()
                    val functionType = functionDecl.parametrized(resultSetMapperType, listOf(rowMapperType))
                    return {
                        ExtensionResult.fromExecutable(functionDecl, functionType)
                    }
                }
            } else {
                if (resultType.declaration.isAnnotationPresent(JdbcTypes.jdbcEntity)) {
                    return fromProcessor(resolver, resultType.declaration.resultSetMapperName())
                }
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
}
