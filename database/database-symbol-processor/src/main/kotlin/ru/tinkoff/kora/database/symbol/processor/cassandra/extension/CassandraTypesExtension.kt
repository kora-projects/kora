package ru.tinkoff.kora.database.symbol.processor.cassandra.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getFunctionDeclarationsByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Variance
import ru.tinkoff.kora.database.symbol.processor.cassandra.CassandraEntityGenerator
import ru.tinkoff.kora.database.symbol.processor.cassandra.CassandraTypes
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.CommonClassNames.isList
import ru.tinkoff.kora.ksp.common.KspCommonUtils.parametrized

//CassandraRowMapper<T>
//CassandraResultSetMapper<List<T>>
class CassandraTypesExtension(val resolver: Resolver, val kspLogger: KSPLogger, val codeGenerator: CodeGenerator) : KoraExtension {
    private val entityGenerator = CassandraEntityGenerator(codeGenerator)

    override fun getDependencyGenerator(resolver: Resolver, type: KSType, tag: String?): (() -> ExtensionResult)? {
        if (tag != null) return null
        if (type.declaration.qualifiedName?.asString()?.equals(CassandraTypes.resultSetMapper.canonicalName) == true) {
            return this.generateResultSetMapper(resolver, type)
        }
        if (type.declaration.qualifiedName?.asString()?.equals(CassandraTypes.asyncResultSetMapper.canonicalName) == true) {
            return this.generateAsyncResultSetMapper(resolver, type)
        }
        if (type.declaration.qualifiedName?.asString()?.equals(CassandraTypes.rowMapper.canonicalName) == true) {
            return this.generateRowMapper(resolver, type)
        }
        if (type.declaration.qualifiedName?.asString()?.equals(CassandraTypes.parameterColumnMapper.canonicalName) == true) {
            return this.generateParameterColumnMapper(resolver, type)
        }
        if (type.declaration.qualifiedName?.asString()?.equals(CassandraTypes.rowColumnMapper.canonicalName) == true) {
            return this.generateRowColumnMapper(resolver, type)
        }

        return null
    }

    private fun generateResultSetMapper(resolver: Resolver, rowSetKSType: KSType): (() -> ExtensionResult)? {
        val rowSetParam = rowSetKSType.arguments[0].type!!.resolve()
        if (rowSetParam.isMarkedNullable) {
            return null
        }
        if (!rowSetParam.isList()) {
            if (rowSetParam.declaration.isAnnotationPresent(CassandraTypes.entity)) {
                return generatedByProcessor(resolver, rowSetParam.declaration as KSClassDeclaration, CassandraTypes.resultSetMapper)
            }
            val resultSetMapperDecl = resolver.getClassDeclarationByName(CassandraTypes.resultSetMapper.canonicalName)!!
            val rowMapperDecl = resolver.getClassDeclarationByName(CassandraTypes.rowMapper.canonicalName)!!
            val resultSetMapperType = resultSetMapperDecl.asType(listOf(resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(rowSetParam), Variance.INVARIANT)))
            val rowMapperType = rowMapperDecl.asType(listOf(resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(rowSetParam), Variance.INVARIANT)))

            val functionDecl = resolver.getFunctionDeclarationsByName(CassandraTypes.resultSetMapper.canonicalName + ".singleResultSetMapper").first()
            val functionType = functionDecl.parametrized(resultSetMapperType, listOf(rowMapperType))
            return {
                ExtensionResult.fromExecutable(functionDecl, functionType)
            }
        }
        val rowType = rowSetParam.arguments[0]
        val rowResolvedType = rowType.type!!.resolve()
        if (rowResolvedType.declaration.isAnnotationPresent(CassandraTypes.entity)) {
            return generatedByProcessor(resolver, rowResolvedType.declaration as KSClassDeclaration, "ListCassandraResultSetMapper")
        }
        val resultSetMapperDecl = resolver.getClassDeclarationByName(CassandraTypes.resultSetMapper.canonicalName)!!
        val rowMapperDecl = resolver.getClassDeclarationByName(CassandraTypes.rowMapper.canonicalName)!!

        val resultSetMapperType = resultSetMapperDecl.asType(
            listOf(
                resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(rowSetParam), Variance.INVARIANT)
            )
        )
        val rowMapperType = rowMapperDecl.asType(
            listOf(
                resolver.getTypeArgument(rowSetParam.arguments[0].type!!, Variance.INVARIANT)
            )
        )

        val functionDecl = resolver.getFunctionDeclarationsByName(CassandraTypes.resultSetMapper.canonicalName + ".listResultSetMapper").first()
        val functionType = functionDecl.parametrized(resultSetMapperType, listOf(rowMapperType))
        return {
            ExtensionResult.fromExecutable(functionDecl, functionType)
        }
    }

    private fun generateRowMapper(resolver: Resolver, rowKSType: KSType): (() -> ExtensionResult)? {
        val rowType = rowKSType.arguments[0].type!!.resolve()
        if (rowType.declaration.isAnnotationPresent(CassandraTypes.entity)) {
            return generatedByProcessor(resolver, rowType.declaration as KSClassDeclaration, CassandraTypes.rowMapper)
        }
        return null
    }

    private fun generateAsyncResultSetMapper(resolver: Resolver, type: KSType): (() -> ExtensionResult)? {
        val resultType = type.arguments[0].type!!.resolve()
        if (resultType.isMarkedNullable) {
            return null
        }
        val resultClassDecl = resultType.declaration as KSClassDeclaration
        if (resultClassDecl.qualifiedName?.asString() == "kotlin.collections.List") {
            val rowType = resultType.arguments[0].type!!.resolve()
            if (rowType.isMarkedNullable) {
                return null
            }
            val resultSetMapperDecl = resolver.getClassDeclarationByName(CassandraTypes.asyncResultSetMapper.canonicalName)!!
            val rowMapperDecl = resolver.getClassDeclarationByName(CassandraTypes.rowMapper.canonicalName)!!

            val resultSetMapperType = resultSetMapperDecl.asType(
                listOf(
                    resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(resultType), Variance.INVARIANT)
                )
            )
            val rowMapperType = rowMapperDecl.asType(
                listOf(
                    resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(rowType), Variance.INVARIANT)
                )
            )

            val functionDecl = resolver.getFunctionDeclarationsByName(CassandraTypes.asyncResultSetMapper.canonicalName + ".list").first()
            val functionType = functionDecl.parametrized(resultSetMapperType, listOf(rowMapperType))
            return {
                ExtensionResult.fromExecutable(functionDecl, functionType)
            }
        } else {
            val resultSetMapperDecl = resolver.getClassDeclarationByName(CassandraTypes.asyncResultSetMapper.canonicalName)!!
            val rowMapperDecl = resolver.getClassDeclarationByName(CassandraTypes.rowMapper.canonicalName)!!

            val resultSetMapperType = resultSetMapperDecl.asType(
                listOf(
                    resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(resultType), Variance.INVARIANT)
                )
            )
            val rowMapperType = rowMapperDecl.asType(
                listOf(
                    resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(resultType), Variance.INVARIANT)
                )
            )

            val functionDecl = resolver.getFunctionDeclarationsByName(CassandraTypes.asyncResultSetMapper.canonicalName + ".one").first()
            val functionType = functionDecl.parametrized(resultSetMapperType, listOf(rowMapperType))
            return {
                ExtensionResult.fromExecutable(functionDecl, functionType)
            }
        }
    }

    private fun generateParameterColumnMapper(resolver: Resolver, type: KSType): (() -> ExtensionResult)? {
        val entityType = type.arguments[0].type!!.resolve()
        if (entityType.isMarkedNullable) {
            return null
        }
        val ksClassDeclaration = entityType.declaration as KSClassDeclaration
        if (ksClassDeclaration.findAnnotation(CassandraTypes.udt) != null) {
            return generatedByProcessor(resolver, ksClassDeclaration, "CassandraParameterColumnMapper")
        }
        if (ksClassDeclaration.qualifiedName?.asString() == "kotlin.collections.List") {
            val t = entityType.arguments[0].type!!.resolve()
            if (t.isMarkedNullable) {
                return null
            }
            val listElementClassDeclaration = t.declaration as KSClassDeclaration
            if (listElementClassDeclaration.findAnnotation(CassandraTypes.udt) != null) {
                return generatedByProcessor(resolver, listElementClassDeclaration, "List_CassandraParameterColumnMapper")
            }
        }
        return null
    }

    private fun generateRowColumnMapper(resolver: Resolver, type: KSType): (() -> ExtensionResult)? {
        val entityType = type.arguments[0].type!!.resolve()
        if (entityType.isMarkedNullable) {
            return null
        }
        val ksClassDeclaration = entityType.declaration as KSClassDeclaration
        if (ksClassDeclaration.findAnnotation(CassandraTypes.udt) != null) {
            return generatedByProcessor(resolver, ksClassDeclaration, "CassandraRowColumnMapper")
        }
        if (ksClassDeclaration.qualifiedName?.asString() == "kotlin.collections.List") {
            val t = entityType.arguments[0].type!!.resolve()
            if (t.isMarkedNullable) {
                return null
            }
            val listKsClassDeclaration = t.declaration as KSClassDeclaration
            if (listKsClassDeclaration.findAnnotation(CassandraTypes.udt) != null) {
                return generatedByProcessor(resolver, listKsClassDeclaration, "List_CassandraRowColumnMapper")
            }
        }

        return null
    }

}
