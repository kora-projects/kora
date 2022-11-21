package ru.tinkoff.kora.database.symbol.processor.r2dbc.extension

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
import ru.tinkoff.kora.database.symbol.processor.model.DbEntity
import ru.tinkoff.kora.database.symbol.processor.r2dbc.R2dbcNativeTypes
import ru.tinkoff.kora.database.symbol.processor.r2dbc.R2dbcTypes
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension
import ru.tinkoff.kora.ksp.common.getOuterClassesAsPrefix
import javax.annotation.processing.Generated

//R2dbcRowMapper<T>
class R2dbcTypesExtension(val resolver: Resolver, val kspLogger: KSPLogger, val codeGenerator: CodeGenerator) : KoraExtension {
    private val entityReader: DbEntityReader = DbEntityReader(
        R2dbcTypes.resultColumnMapper,
        { CodeBlock.of("%N.apply(_row, %S)", it.mapperFieldName, it.columnName) },
        { R2dbcNativeTypes.findNativeType(it.type.toTypeName())?.extract("_row", CodeBlock.of("%S", it.columnName)) },
        {
            CodeBlock.of(
                "if (%N == null) {\n  throw %T(%S);\n}\n",
                it.fieldName,
                NullPointerException::class.asClassName(),
                "Required field ${it.columnName} is not nullable but row has null"
            )
        }
    )

    override fun getDependencyGenerator(resolver: Resolver, type: KSType): (() -> ExtensionResult)? {
        if (type.declaration.qualifiedName?.asString()?.equals(R2dbcTypes.rowMapper.canonicalName) == true) {
            return this.generateRowMapper(resolver, type)
        }
        return null
    }

    private fun generateRowMapper(resolver: Resolver, rowKSType: KSType): (() -> ExtensionResult)? {
        val rowType = rowKSType.arguments[0]
        val entity = DbEntity.parseEntity(rowType.type!!.resolve())
        if (entity == null) {
            return null
        }
        val typeName = rowType.type!!.resolve().toClassName().simpleName
        val mapperName = rowKSType.declaration.getOuterClassesAsPrefix() + "${typeName}_R2dbcRowMapper"
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
                .addAnnotation(AnnotationSpec.builder(Generated::class).addMember("value = [%S]", R2dbcTypesExtension::class.qualifiedName!!).build())
                .addSuperinterface(R2dbcTypes.rowMapper.parameterizedBy(entity.type.toTypeName()))

            val constructor = FunSpec.constructorBuilder()
            val apply = FunSpec.builder("apply")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("_row", R2dbcTypes.row)
                .returns(entity.type.toTypeName())

            val read = this.entityReader.readEntity("_result", entity)
            read.enrich(type, constructor)
            apply.addCode(read.block)
            apply.addStatement("return _result")


            type.primaryConstructor(constructor.build())
            type.addFunction(apply.build())

            FileSpec.get(packageName, type.build()).writeTo(codeGenerator, true, listOfNotNull(entity.type.declaration.containingFile))

            ExtensionResult.RequiresCompilingResult
        }
    }
}
