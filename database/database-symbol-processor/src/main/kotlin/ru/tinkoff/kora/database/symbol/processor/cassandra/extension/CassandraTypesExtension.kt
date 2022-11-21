package ru.tinkoff.kora.database.symbol.processor.cassandra.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
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
import ru.tinkoff.kora.ksp.common.CommonClassNames.isList
import ru.tinkoff.kora.ksp.common.getOuterClassesAsPrefix
import javax.annotation.processing.Generated

//CassandraRowMapper<T>
//CassandraResultSetMapper<List<T>>
class CassandraTypesExtension(val resolver: Resolver, val kspLogger: KSPLogger, val codeGenerator: CodeGenerator) : KoraExtension {
    private val entityReader: DbEntityReader = DbEntityReader(
        CassandraTypes.resultColumnMapper,
        { CodeBlock.of("%N.apply(_row, _idx_%L)", it.mapperFieldName, it.fieldName) },
        { CassandraNativeTypes.findNativeType(it.type.toTypeName())?.extract("_row", CodeBlock.of("_idx_%L", it.fieldName)) },
        {
            CodeBlock.of(
                "if (_row.isNull(_idx_%L) || %N == null) {\n  throw %T(%S);\n}\n",
                it.fieldName,
                it.fieldName,
                NullPointerException::class.asClassName(),
                "Required field ${it.columnName} is not nullable but row has null"
            )
        }
    )

    override fun getDependencyGenerator(resolver: Resolver, type: KSType): (() -> ExtensionResult)? {
        if (type.declaration.qualifiedName?.asString()?.equals(CassandraTypes.resultSetMapper.canonicalName) == true) {
            return this.generateResultSetMapper(resolver, type)
        }
        if (type.declaration.qualifiedName?.asString()?.equals(CassandraTypes.rowMapper.canonicalName) == true) {
            return this.generateRowMapper(resolver, type)
        }
        return null
    }

    private fun generateResultSetMapper(resolver: Resolver, rowSetKSType: KSType): (() -> ExtensionResult)? {
        val rowSetParam = rowSetKSType.arguments[0].type!!.resolve()
        if (!rowSetParam.isList()) {
            return null
        }
        val rowType = rowSetParam.arguments[0]
        val rowResolvedType = rowType.type!!.resolve()
        val entity = DbEntity.parseEntity(rowResolvedType)
        if (entity == null) {
            return null
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
                .addAnnotation(AnnotationSpec.builder(Generated::class).addMember("value = [%S]", CassandraTypesExtension::class.qualifiedName!!).build())
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
        val rowType = rowKSType.arguments[0]
        val entity = DbEntity.parseEntity(rowType.type!!.resolve())
        if (entity == null) {
            return null
        }
        val typeName = rowType.type!!.resolve().toClassName().simpleName
        val mapperName = rowKSType.declaration.getOuterClassesAsPrefix() + "${typeName}_CassandraRowMapper"
        val packageName = rowKSType.declaration.packageName.asString()

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
                .addAnnotation(AnnotationSpec.builder(Generated::class).addMember("value = [%S]", CassandraTypesExtension::class.qualifiedName!!).build())
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

            FileSpec.get(packageName, type.build()).writeTo(codeGenerator, true, listOfNotNull(entity.type.declaration.containingFile))

            ExtensionResult.RequiresCompilingResult
        }
    }

    private fun parseIndexes(entity: DbEntity, rsName: String): CodeBlock {
        val cb = CodeBlock.builder()
        for (field in entity.fields) {
            cb.add("val _idx_%L = %N.columnDefinitions.firstIndexOf(%S);\n", field.property.simpleName.getShortName(), rsName, field.columnName)
        }
        return cb.build()
    }
}
