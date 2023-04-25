package ru.tinkoff.kora.database.symbol.processor.r2dbc.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getFunctionDeclarationsByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Variance
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
import ru.tinkoff.kora.ksp.common.CommonClassNames.isFlux
import ru.tinkoff.kora.ksp.common.CommonClassNames.isList
import ru.tinkoff.kora.ksp.common.CommonClassNames.isMono
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.KspCommonUtils.parametrized
import ru.tinkoff.kora.ksp.common.getOuterClassesAsPrefix

//R2dbcRowMapper<T>
class R2dbcTypesExtension(val resolver: Resolver, val kspLogger: KSPLogger, val codeGenerator: CodeGenerator) : KoraExtension {
    private val entityReader: DbEntityReader = DbEntityReader(
        R2dbcTypes.resultColumnMapper,
        { CodeBlock.of("%N.apply(_row, %S)", it.mapperFieldName, it.columnName) },
        { R2dbcNativeTypes.findNativeType(it.type.toTypeName())?.extract("_row", CodeBlock.of("%S", it.columnName)) },
        {
            if (it.isNullable) {
                CodeBlock.of("")
            } else {
                CodeBlock.builder().controlFlow("if (%N == null)", it.fieldName) {
                    addStatement("throw %T(%S)", NullPointerException::class.asClassName(), "Required field ${it.columnName} is not nullable but row has null")
                }
                    .build()
            }
        }
    )

    override fun getDependencyGenerator(resolver: Resolver, type: KSType): (() -> ExtensionResult)? {
        if (type.declaration.qualifiedName?.asString()?.equals(R2dbcTypes.rowMapper.canonicalName) == true) {
            return this.generateRowMapper(resolver, type)
        }
        if (type.declaration.qualifiedName?.asString()?.equals(R2dbcTypes.resultFluxMapper.canonicalName) == true) {
            val resultPublisher = type.arguments[1].type!!.resolve()
            if (resultPublisher.isFlux()) {
                return this.generateResultFluxMapperForFlux(resolver, type, resultPublisher)
            }
            if (resultPublisher.isMono()) {
                return this.generateResultFluxMapperForMono(resolver, type, resultPublisher)
            }
        }
        return null
    }

    private fun generateResultFluxMapperForFlux(resolver: Resolver, type: KSType, resultPublisher: KSType): (() -> ExtensionResult)? {
        val rowType = resultPublisher.arguments[0].type!!
        val resultSetMapperDecl = resolver.getClassDeclarationByName(R2dbcTypes.resultFluxMapper.canonicalName)!!
        val rowMapperDecl = resolver.getClassDeclarationByName(R2dbcTypes.rowMapper.canonicalName)!!

        val resultSetMapperType = resultSetMapperDecl.asType(
            listOf(
                resolver.getTypeArgument(type.arguments[0].type!!, Variance.INVARIANT),
                resolver.getTypeArgument(type.arguments[1].type!!, Variance.INVARIANT)
            )
        )
        val rowMapperType = rowMapperDecl.asType(
            listOf(
                resolver.getTypeArgument(rowType, Variance.INVARIANT)
            )
        )

        val functionDecl = resolver.getFunctionDeclarationsByName(R2dbcTypes.resultFluxMapper.canonicalName + ".flux").first()
        val functionType = functionDecl.parametrized(resultSetMapperType, listOf(rowMapperType))
        return {
            ExtensionResult.fromExecutable(functionDecl, functionType)
        }
    }

    private fun generateResultFluxMapperForMono(resolver: Resolver, type: KSType, resultPublisher: KSType): (() -> ExtensionResult)? {
        val resultType = resultPublisher.arguments[0].type!!.resolve()
        if (!resultType.isList()) {
            return null
        }
        val resultSetMapperDecl = resolver.getClassDeclarationByName(R2dbcTypes.resultFluxMapper.canonicalName)!!
        val rowMapperDecl = resolver.getClassDeclarationByName(R2dbcTypes.rowMapper.canonicalName)!!

        val resultSetMapperType = resultSetMapperDecl.asType(
            listOf(
                resolver.getTypeArgument(type.arguments[0].type!!, Variance.INVARIANT),
                resolver.getTypeArgument(type.arguments[1].type!!, Variance.INVARIANT)
            )
        )
        val rowMapperType = rowMapperDecl.asType(
            listOf(
                resolver.getTypeArgument(resultType.arguments[0].type!!, Variance.INVARIANT)
            )
        )

        val functionDecl = resolver.getFunctionDeclarationsByName(R2dbcTypes.resultFluxMapper.canonicalName + ".monoList").first()
        val functionType = functionDecl.parametrized(resultSetMapperType, listOf(rowMapperType))
        return {
            ExtensionResult.fromExecutable(functionDecl, functionType)
        }
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
                .generated(R2dbcTypesExtension::class)
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
