package ru.tinkoff.kora.database.symbol.processor.jdbc.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getFunctionDeclarationsByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Variance
import ru.tinkoff.kora.database.symbol.processor.jdbc.JdbcTypes
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.CommonClassNames.isList
import ru.tinkoff.kora.ksp.common.KspCommonUtils.parametrized

// JdbcRowMapper<T>
// JdbcResultSetMapper<T>
// JdbcResultSetMapper<List<T>>
class JdbcTypesExtension() : KoraExtension {
    override fun getDependencyGenerator(resolver: Resolver, type: KSType, tags: Set<String>): (() -> ExtensionResult)? {
        if (tags.isNotEmpty()) return null
        if (type.declaration.qualifiedName == null) {
            return null
        }
        if (type.declaration.qualifiedName!!.asString() == JdbcTypes.jdbcRowMapper.canonicalName) {
            val rowType = type.arguments[0].type!!.resolve()
            if (rowType.isMarkedNullable) {
                return null
            }
            if (rowType.declaration.isAnnotationPresent(JdbcTypes.jdbcEntity)) {
                return generatedByProcessor(resolver, rowType.declaration as KSClassDeclaration, JdbcTypes.jdbcRowMapper)
            }
            return null
        }
        if (type.declaration.qualifiedName!!.asString() == JdbcTypes.jdbcResultSetMapper.canonicalName) {
            val resultType = type.arguments[0].type!!.resolve()
            if (resultType.isMarkedNullable) {
                return null
            }
            if (resultType.isList()) {
                val rowType = resultType.arguments[0].type!!.resolve()
                if (rowType.isMarkedNullable) {
                    return null
                }
                if (rowType.declaration.isAnnotationPresent(JdbcTypes.jdbcEntity)) {
                    return generatedByProcessor(resolver, rowType.declaration as KSClassDeclaration, "ListJdbcResultSetMapper")
                }

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
            } else {
                if (resultType.declaration.isAnnotationPresent(JdbcTypes.jdbcEntity)) {
                    return generatedByProcessor(resolver, resultType.declaration as KSClassDeclaration, JdbcTypes.jdbcResultSetMapper)
                }
                return {
                    val resultSetMapperDecl = resolver.getClassDeclarationByName(JdbcTypes.jdbcResultSetMapper.canonicalName)!!
                    val rowMapperDecl = resolver.getClassDeclarationByName(JdbcTypes.jdbcRowMapper.canonicalName)!!

                    val resultSetMapperType = resultSetMapperDecl.asType(
                        listOf(
                            resolver.getTypeArgument(
                                resolver.createKSTypeReferenceFromKSType(resultType), Variance.INVARIANT
                            )
                        )
                    )
                    val rowMapperType = rowMapperDecl.asType(
                        listOf(
                            resolver.getTypeArgument(
                                resolver.createKSTypeReferenceFromKSType(resultType), Variance.INVARIANT
                            )
                        )
                    )

                    val functionDecl = resolver.getFunctionDeclarationsByName(JdbcTypes.jdbcResultSetMapper.canonicalName + ".singleResultSetMapper").first()
                    val functionType = functionDecl.parametrized(resultSetMapperType, listOf(rowMapperType))
                    ExtensionResult.fromExecutable(functionDecl, functionType)
                }
            }
        }
        return null
    }
}
