package ru.tinkoff.kora.database.symbol.processor.vertx.extension

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
import ru.tinkoff.kora.database.symbol.processor.model.DbEntity
import ru.tinkoff.kora.database.symbol.processor.vertx.VertxNativeTypes
import ru.tinkoff.kora.database.symbol.processor.vertx.VertxTypes
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.getOuterClassesAsPrefix

//RowMapper<T>
//RowSetMapper<List<T>>
class VertxTypesExtension(val resolver: Resolver, val kspLogger: KSPLogger, val codeGenerator: CodeGenerator) : KoraExtension {
    private val entityReader: DbEntityReader = DbEntityReader(
        VertxTypes.resultColumnMapper,
        { CodeBlock.of("`%L`.apply(`\$row`, `\$idx_%L`)", it.mapperFieldName, it.fieldName) },
        { VertxNativeTypes.findNativeType(it.type.toTypeName())?.extract("`\$row`", "`\$idx_${it.fieldName}`") },
        {
            CodeBlock.of(
                "if (`%L` == null) {\n  throw %T(%S);\n}\n",
                it.fieldName,
                NullPointerException::class.asClassName(),
                "Required field ${it.columnName} is not nullable but row has null"
            )
        }
    )

    override fun getDependencyGenerator(resolver: Resolver, type: KSType): (() -> ExtensionResult)? {
        if (type.declaration.qualifiedName?.asString()?.equals(VertxTypes.rowMapper.canonicalName) == true) {
            val rowType = type.arguments[0].type!!.resolve()
            return this.generateRowMapper(resolver, rowType)
        }
        if (type.declaration.qualifiedName?.asString()?.equals(VertxTypes.rowSetMapper.canonicalName) == true) {
            val rowSetArg = type.arguments[0]
            if (rowSetArg.type!!.resolve().declaration.qualifiedName?.asString()?.equals("kotlin.collections.List") == true) {
                return this.generateListRowSetMapper(resolver, type)
            }
        }
        return null
    }


    private fun generateRowMapper(resolver: Resolver, rowKSType: KSType): (() -> ExtensionResult)? {
        val entity = DbEntity.parseEntity(rowKSType)
        if (entity == null) {
            return null
        }
        val mapperName = rowKSType.declaration.getOuterClassesAsPrefix() + entity.classDeclaration.simpleName.getShortName() + "_VertxRowMapper"
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
                .generated(VertxTypesExtension::class)
                .addSuperinterface(VertxTypes.rowMapper.parameterizedBy(entity.type.toTypeName()))

            val constructor = FunSpec.constructorBuilder()
            val apply = FunSpec.builder("apply")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("\$row", VertxTypes.row)
                .returns(entity.type.toTypeName())

            for (field in entity.fields) {
                apply.addCode("val `\$idx_%L` = `\$row`.getColumnIndex(%S);\n", field.property.simpleName.getShortName(), field.columnName)
            }

            val read = this.entityReader.readEntity("\$result", entity)
            read.enrich(type, constructor)
            apply.addCode(read.block)
            apply.addCode("return `\$result`;\n")

            type.primaryConstructor(constructor.build())
            type.addFunction(apply.build())

            FileSpec.get(packageName, type.build()).writeTo(codeGenerator, true, listOfNotNull(entity.type.declaration.containingFile))

            ExtensionResult.RequiresCompilingResult
        }
    }

    private fun generateListRowSetMapper(resolver: Resolver, ksType: KSType): (() -> ExtensionResult)? {
        val rowSetArg = ksType.arguments[0]
        val rowType = rowSetArg.type!!.resolve().arguments[0].type!!.resolve()
        val entity = DbEntity.parseEntity(rowType)
        if (entity == null) {
            return null
        }
        val mapperName = entity.classDeclaration.getOuterClassesAsPrefix() + entity.classDeclaration.simpleName.getShortName() + "_VertxListRowSetMapper"
        val packageName = entity.classDeclaration.packageName.asString()

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
                .generated(VertxTypesExtension::class)
                .addSuperinterface(VertxTypes.rowSetMapper.parameterizedBy(rowSetArg.type!!.toTypeName()))

            val readEntity = entityReader.readEntity("\$rowValue", entity)

            val constructor = FunSpec.constructorBuilder()
            readEntity.enrich(type, constructor)
            val apply = FunSpec.builder("apply")
                .addModifiers(KModifier.OVERRIDE)
                .returns(rowSetArg.type!!.toTypeName())
                .addParameter("\$rs", VertxTypes.rowSet)

            for (field in entity.fields) {
                apply.addCode("val `\$idx_%L` = `\$rs`.columnsNames().indexOf(%S);\n", field.property.simpleName.getShortName(), field.columnName)
            }
            apply.addStatement("var `\$result` = %T<%T>(`\$rs`.rowCount())", ArrayList::class, rowType.toTypeName())
            apply.controlFlow("for (`\$row` in `\$rs`)") {
                apply.addCode(readEntity.block)
                apply.addStatement("`\$result`.add(`\$rowValue`)")
            }
                .addStatement("return `\$result`")

            type.primaryConstructor(constructor.build())
            type.addFunction(apply.build())

            FileSpec.get(packageName, type.build()).writeTo(codeGenerator, true, listOfNotNull(entity.type.declaration.containingFile))

            ExtensionResult.RequiresCompilingResult
        }
    }
}
