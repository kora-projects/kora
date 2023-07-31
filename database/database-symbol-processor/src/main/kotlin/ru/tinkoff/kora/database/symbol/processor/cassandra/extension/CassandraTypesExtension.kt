package ru.tinkoff.kora.database.symbol.processor.cassandra.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getFunctionDeclarationsByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Variance
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.database.symbol.processor.DbEntityReader
import ru.tinkoff.kora.database.symbol.processor.cassandra.CassandraNativeTypes
import ru.tinkoff.kora.database.symbol.processor.cassandra.CassandraTypes
import ru.tinkoff.kora.database.symbol.processor.model.DbEntity
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.CommonClassNames.isList
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.KspCommonUtils.parametrized
import ru.tinkoff.kora.ksp.common.generatedClassName
import ru.tinkoff.kora.ksp.common.getOuterClassesAsPrefix

//CassandraRowMapper<T>
//CassandraResultSetMapper<List<T>>
class CassandraTypesExtension(val resolver: Resolver, val kspLogger: KSPLogger, val codeGenerator: CodeGenerator) : KoraExtension {
    private val entityReader: DbEntityReader = DbEntityReader(
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

    override fun getDependencyGenerator(resolver: Resolver, type: KSType, tags: Set<String>): (() -> ExtensionResult)? {
        if (tags.isNotEmpty()) return null
        if (type.declaration.qualifiedName?.asString()?.equals(CassandraTypes.resultSetMapper.canonicalName) == true) {
            return this.generateResultSetMapper(resolver, type)
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
        if (!rowSetParam.isList()) {
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
        if (entity == null) {
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
        val typeName = rowResolvedType.toClassName().simpleName
        val mapperName = rowResolvedType.declaration.getOuterClassesAsPrefix() + "${typeName}_CassandraListRowSetMapper"
        val packageName = rowResolvedType.declaration.packageName.asString()

        return lambda@{
            val maybeGenerated = resolver.getClassDeclarationByName("$packageName.$mapperName")
            if (maybeGenerated != null) {
                val constructor = maybeGenerated.primaryConstructor
                if (constructor == null) {
                    throw IllegalStateException()
                }
                return@lambda ExtensionResult.fromConstructor(constructor, maybeGenerated)
            }
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
            apply.addCode("val _result = %T<%T>(_rs.availableWithoutFetching);\n", ArrayList::class, entityTypeName)
            apply.addCode(parseIndexes(entity, "_rs"))
            val read = this.entityReader.readEntity("_mappedRow", entity)
            read.enrich(type, constructor)
            apply.beginControlFlow("for (_row in _rs)")
            apply.addCode(read.block)
            apply.addCode("_result.add(_mappedRow)\n")
            apply.endControlFlow()

            apply.addCode("return _result;\n")


            type.primaryConstructor(constructor.build())
            type.addFunction(apply.build())

            FileSpec.get(packageName, type.build()).writeTo(codeGenerator, true, listOfNotNull(entity.type.declaration.containingFile))

            ExtensionResult.RequiresCompilingResult
        }
    }

    private fun generateRowMapper(resolver: Resolver, rowKSType: KSType): (() -> ExtensionResult)? {
        val rowType = rowKSType.arguments[0].type!!.resolve()
        val entity = DbEntity.parseEntity(rowType)
        if (entity == null) {
            return null
        }
        val rowClassDeclaration = rowType.declaration as KSClassDeclaration
        val mapperName = rowClassDeclaration.generatedClassName("CassandraRowMapper")
        val packageName = rowClassDeclaration.packageName.asString()

        return lambda@{
            val maybeGenerated = resolver.getClassDeclarationByName("$packageName.$mapperName")
            if (maybeGenerated != null) {
                val constructor = maybeGenerated.primaryConstructor
                if (constructor == null) {
                    throw IllegalStateException()
                }
                return@lambda ExtensionResult.fromConstructor(constructor, maybeGenerated)
            }
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

            FileSpec.get(packageName, type.build()).writeTo(codeGenerator, true, listOfNotNull(rowClassDeclaration.containingFile))

            ExtensionResult.RequiresCompilingResult
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
            val ksClassDeclaration = t.declaration as KSClassDeclaration
            if (ksClassDeclaration.findAnnotation(CassandraTypes.udt) != null) {
                return generatedByProcessor(resolver, ksClassDeclaration, "List_CassandraParameterColumnMapper")
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
            val ksClassDeclaration = t.declaration as KSClassDeclaration
            if (ksClassDeclaration.findAnnotation(CassandraTypes.udt) != null) {
                return generatedByProcessor(resolver, ksClassDeclaration, "List_CassandraRowColumnMapper")
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
}
