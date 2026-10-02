package io.koraframework.database.symbol.processor.mongo

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.database.symbol.processor.DbUtils
import io.koraframework.database.symbol.processor.DbUtils.operationName
import io.koraframework.database.symbol.processor.DbUtils.parseExecutorTag
import io.koraframework.database.symbol.processor.DbUtils.queryMethodBuilder
import io.koraframework.database.symbol.processor.RepositoryGenerator
import io.koraframework.ksp.common.FieldFactory
import io.koraframework.ksp.common.KotlinPoetUtils.observe
import io.koraframework.ksp.common.exception.ProcessingErrorException

class MongoRepositoryGenerator(private val resolver: Resolver) : RepositoryGenerator {

    companion object {
        const val EXECUTOR_FIELD = "_mongoExecutor"
    }

    private val repositoryInterface = resolver.getClassDeclarationByName(MongoTypes.repository.canonicalName)?.asStarProjectedType()
    private val operations = MongoOperationGenerator(resolver)

    override fun repositoryInterface() = this.repositoryInterface

    override fun generate(repositoryType: KSClassDeclaration, typeBuilder: TypeSpec.Builder, constructorBuilder: FunSpec.Builder): TypeSpec {
        this.enrichWithExecutor(repositoryType, typeBuilder, constructorBuilder)
        val codecs = FieldFactory(typeBuilder, constructorBuilder, "_codec_")
        val registries = MongoCodecRegistries(typeBuilder, constructorBuilder)

        var number = 1
        for (method in this.findOperationMethods(repositoryType)) {
            typeBuilder.addFunction(this.generateMethod(repositoryType, typeBuilder, method, number++, codecs, registries))
        }
        return typeBuilder.primaryConstructor(constructorBuilder.build()).build()
    }

    private fun generateMethod(
        repositoryType: KSClassDeclaration,
        typeBuilder: TypeSpec.Builder,
        method: KSFunctionDeclaration,
        number: Int,
        codecs: FieldFactory,
        registries: MongoCodecRegistries
    ): FunSpec {
        for (parameter in method.parameters) {
            val name = parameter.name!!.asString()
            if (name.startsWith("_") || name.startsWith("$")) {
                throw ProcessingErrorException(
                    """
                    Mongo repository parameter name is invalid:
                      ${repositoryType.simpleName.asString()}#${method.simpleName.asString()}($name)

                    Problem:
                      Parameter names starting with '_' or '${'$'}' are reserved for the code Kora generates.

                    Hint:
                      The generated function declares its own locals with these prefixes, and a ':name' placeholder can not start with '${'$'}'.

                    Fix:
                      Rename the parameter so that it starts with a letter.
                    """.trimIndent(), parameter
                )
            }
        }
        val operation = MongoOperation.parse(method)
        val function = method.asMemberOf(repositoryType.asStarProjectedType())
        val returnType = function.returnType!!
        val parameters = MongoParameters(method, codecs)
        val context = MongoOperationGenerator.Context(repositoryType, method, returnType, operation, parameters, codecs, registries)

        val body = CodeBlock.builder()
        body.addStatement("_observation.observeConnection()")
        val description = this.operations.generate(body, context)

        val queryContextField = "_queryContext_$number"
        typeBuilder.addProperty(
            PropertySpec.builder(queryContextField, DbUtils.queryContext, KModifier.PRIVATE)
                .initializer(
                    """
                    %T(
                      %S,
                      %S,
                      %S
                    )
                    """.trimIndent(), DbUtils.queryContext, description, description, method.operationName()
                )
                .build()
        )

        val b = method.queryMethodBuilder(this.resolver)
        b.addStatement("val _query = %L", queryContextField)
        b.addStatement("val _observation = this.%N.telemetry().observe(_query)", EXECUTOR_FIELD)
        b.addCode("return ")
        b.observe("_observation", returnType.toTypeName()) { add(body.build()) }
        return b.build()
    }

    private fun enrichWithExecutor(declaration: KSClassDeclaration, builder: TypeSpec.Builder, constructorBuilder: FunSpec.Builder) {
        builder.addProperty(EXECUTOR_FIELD, MongoTypes.executor, KModifier.PRIVATE)
        builder.addSuperinterface(MongoTypes.repository)
        builder.addFunction(
            FunSpec.builder("executor")
                .addModifiers(KModifier.OVERRIDE)
                .returns(MongoTypes.executor)
                .addStatement("return this.%N", EXECUTOR_FIELD)
                .build()
        )

        val parameter = ParameterSpec.builder(EXECUTOR_FIELD, MongoTypes.executor)
        declaration.parseExecutorTag()?.let { parameter.addAnnotation(it) }
        constructorBuilder.addParameter(parameter.build())
        constructorBuilder.addStatement("this.%N = %N", EXECUTOR_FIELD, EXECUTOR_FIELD)
    }

    /**
     * Methods are sorted so that generated property numbering stays stable between compilations.
     */
    private fun findOperationMethods(declaration: KSClassDeclaration): List<KSFunctionDeclaration> = declaration.getAllFunctions()
        .filter { it.isAbstract }
        .filter { (it.parentDeclaration as? KSClassDeclaration)?.qualifiedName?.asString() != MongoTypes.repository.canonicalName }
        .sortedBy { it.simpleName.asString() }
        .toList()
}
