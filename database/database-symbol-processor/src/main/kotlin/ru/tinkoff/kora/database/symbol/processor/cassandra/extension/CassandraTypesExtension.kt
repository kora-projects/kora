package ru.tinkoff.kora.database.symbol.processor.cassandra.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getFunctionDeclarationsByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Variance
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import ru.tinkoff.kora.database.symbol.processor.cassandra.CassandraEntityGenerator
import ru.tinkoff.kora.database.symbol.processor.cassandra.CassandraEntityGenerator.Companion.listResultSetMapperClassName
import ru.tinkoff.kora.database.symbol.processor.cassandra.CassandraEntityGenerator.Companion.resultSetMapperClassName
import ru.tinkoff.kora.database.symbol.processor.cassandra.CassandraEntityGenerator.Companion.rowMapperClassName
import ru.tinkoff.kora.database.symbol.processor.cassandra.CassandraTypes
import ru.tinkoff.kora.database.symbol.processor.model.DbEntity
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.CommonClassNames.isList
import ru.tinkoff.kora.ksp.common.KspCommonUtils.getClassDeclarationByName
import ru.tinkoff.kora.ksp.common.KspCommonUtils.parametrized

//CassandraRowMapper<T>
//CassandraResultSetMapper<List<T>>
class CassandraTypesExtension(val resolver: Resolver, val kspLogger: KSPLogger, val codeGenerator: CodeGenerator) : KoraExtension {
    private val entityGenerator = CassandraEntityGenerator(codeGenerator)

    override fun getDependencyGenerator(resolver: Resolver, type: KSType, tags: Set<String>): (() -> ExtensionResult)? {
        if (tags.isNotEmpty()) return null
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
            val entity = DbEntity.parseEntity(rowSetParam)
            if (entity != null) {
                if (entity.type.declaration.isAnnotationPresent(CassandraTypes.entity)) {
                    return fromProcessor(resolver, rowSetParam.resultSetMapperClassName())
                } else {
                    return fromExtension(resolver, rowSetKSType, rowSetParam.resultSetMapperClassName()) {
                        entityGenerator.generateResultSetMapper(entity, true)
                    }
                }
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
        val entity = DbEntity.parseEntity(rowResolvedType)
        if (entity != null) {
            if (entity.type.declaration.isAnnotationPresent(CassandraTypes.entity)) {
                return fromProcessor(resolver, rowResolvedType.listResultSetMapperClassName())
            } else {
                return fromExtension(resolver, rowSetKSType, rowResolvedType.listResultSetMapperClassName()) {
                    entityGenerator.generateListResultSetMapper(entity, true)
                }
            }
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
        val entity = DbEntity.parseEntity(rowType)
        if (entity == null) {
            return null
        }
        if (entity.classDeclaration.isAnnotationPresent(CassandraTypes.entity)) {
            return fromProcessor(resolver, rowType.rowMapperClassName())
        } else {
            return fromExtension(resolver, rowKSType, rowType.rowMapperClassName()) {
                entityGenerator.generateRowMapper(entity, true)
            }
        }
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

    private fun parseIndexes(entity: DbEntity, rsName: String): CodeBlock {
        val cb = CodeBlock.builder()
        for (field in entity.columns) {
            cb.add("val %N = %N.columnDefinitions.firstIndexOf(%S)\n", "_idx_${field.variableName}", rsName, field.columnName)
        }
        return cb.build()
    }

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
                kspLogger.warn("Type is not annotated with @EntityCassandra, but mapper $mapperType is requested by graph. Generating one in graph building process will lead to another round of compiling which will slow down you build")
                generator()
                ExtensionResult.RequiresCompilingResult
            }
        }
    }

}
