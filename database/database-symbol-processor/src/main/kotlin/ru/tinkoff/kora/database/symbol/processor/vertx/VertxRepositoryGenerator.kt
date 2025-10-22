package ru.tinkoff.kora.database.symbol.processor.vertx

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunction
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.database.symbol.processor.DbUtils
import ru.tinkoff.kora.database.symbol.processor.DbUtils.addMapper
import ru.tinkoff.kora.database.symbol.processor.DbUtils.asFlow
import ru.tinkoff.kora.database.symbol.processor.DbUtils.findQueryMethods
import ru.tinkoff.kora.database.symbol.processor.DbUtils.operationName
import ru.tinkoff.kora.database.symbol.processor.DbUtils.parseExecutorTag
import ru.tinkoff.kora.database.symbol.processor.DbUtils.queryMethodBuilder
import ru.tinkoff.kora.database.symbol.processor.DbUtils.resultMapperName
import ru.tinkoff.kora.database.symbol.processor.DbUtils.updateCount
import ru.tinkoff.kora.database.symbol.processor.Mapper
import ru.tinkoff.kora.database.symbol.processor.QueryWithParameters
import ru.tinkoff.kora.database.symbol.processor.RepositoryGenerator
import ru.tinkoff.kora.database.symbol.processor.model.QueryParameter
import ru.tinkoff.kora.database.symbol.processor.model.QueryParameterParser
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValue
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.CommonClassNames.isFlow
import ru.tinkoff.kora.ksp.common.CommonClassNames.isList
import ru.tinkoff.kora.ksp.common.FieldFactory
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFlow
import ru.tinkoff.kora.ksp.common.FunctionUtils.isSuspend
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.ksp.common.parseMappingData
import java.util.concurrent.CompletableFuture

class VertxRepositoryGenerator(private val resolver: Resolver, private val kspLogger: KSPLogger) : RepositoryGenerator {
    private val repositoryInterface = resolver.getClassDeclarationByName(resolver.getKSNameFromString(VertxTypes.repository.canonicalName))?.asStarProjectedType()
    override fun repositoryInterface() = repositoryInterface

    override fun generate(repositoryType: KSClassDeclaration, typeBuilder: TypeSpec.Builder, constructorBuilder: FunSpec.Builder): TypeSpec {
        this.enrichWithExecutor(repositoryType, typeBuilder, constructorBuilder)
        val repositoryResolvedType = repositoryType.asStarProjectedType()
        val resultMappers = FieldFactory(typeBuilder, constructorBuilder, "_result_mapper_")
        val parameterMappers = FieldFactory(typeBuilder, constructorBuilder, "_parameter_mapper_")

        var methodCounter = 1
        for (method in repositoryType.findQueryMethods()) {
            val methodType = method.asMemberOf(repositoryResolvedType)
            val parameters = QueryParameterParser.parse(listOf(VertxTypes.sqlConnection, VertxTypes.sqlClient), VertxTypes.parameterColumnMapper, method, methodType)
            val queryAnnotation = method.findAnnotation(DbUtils.queryAnnotation)!!
            val queryString = queryAnnotation.findValue<String>("value")!!
            val query = QueryWithParameters.parse(queryString, parameters, method)
            val resultMapperName = this.parseResultMapper(method, parameters, methodType)?.let { resultMappers.addMapper(it) }
            DbUtils.parseParameterMappers(method, parameters, query, VertxTypes.parameterColumnMapper) { VertxNativeTypes.findNativeType(it.toTypeName()) != null }
                .forEach { parameterMappers.addMapper(it) }
            val methodSpec = this.generate(typeBuilder, methodCounter, method, methodType, query, parameters, resultMapperName, parameterMappers)
            typeBuilder.addFunction(methodSpec)
            methodCounter++
        }

        return typeBuilder.primaryConstructor(constructorBuilder.build()).build()
    }

    private data class QueryReplace(val index: Int, val paramIndex: Int, val name: String)

    private fun generate(
        typeBuilder: TypeSpec.Builder,
        methodNumber: Int,
        funDeclaration: KSFunctionDeclaration,
        function: KSFunction,
        query: QueryWithParameters,
        parameters: List<QueryParameter>,
        resultMapperName: String?,
        parameterMappers: FieldFactory
    ): FunSpec {
        var sql = query.rawQuery

        val replaceParams = mutableListOf<QueryReplace>()
        for (i in query.parameters.indices) {
            val parameter = query.parameters[i]
            for (queryIndex in parameter.queryIndexes) {
                replaceParams.add(QueryReplace(queryIndex, i + 1, parameter.sqlParameterName))
            }
        }
        replaceParams.sortBy { it.index }
        var sqlIndexDiff = 0
        for (parameter in replaceParams) {
            val queryIndexAdjusted: Int = parameter.index - sqlIndexDiff
            sql = sql.substring(0, queryIndexAdjusted) + "$" + parameter.paramIndex + sql.substring(queryIndexAdjusted + parameter.name.length + 1)
            sqlIndexDiff += (parameter.name.length - parameter.paramIndex.toString().length)
        }

        val b = funDeclaration.queryMethodBuilder(resolver)

        val queryContextFieldName = "_queryContext_$methodNumber"
        typeBuilder.addProperty(
            PropertySpec.builder(queryContextFieldName, DbUtils.queryContext, KModifier.PRIVATE)
                .initializer(
                    """
                    %T(
                      %S,
                      %S,
                      %S
                    )
                    """.trimIndent(), DbUtils.queryContext, query.rawQuery, sql, funDeclaration.operationName()
                )
                .build()
        )
        b.addStatement("val _query = %L", queryContextFieldName)

        val batchParam = parameters.firstOrNull { it is QueryParameter.BatchParameter }
        val isSuspend = funDeclaration.isSuspend()
        val isFlow = funDeclaration.isFlow()
        ParametersToTupleBuilder.generate(b, query, parameters, batchParam, parameterMappers)
        val connectionParameter = parameters.asSequence().filterIsInstance<QueryParameter.ConnectionParameter>().firstOrNull()?.variable?.name?.asString()

        if (batchParam != null) {
            b.addCode("var _future = ")
            if (connectionParameter == null) {
                b.addCode("%T.batchCompletionStage(this._vertxConnectionFactory, _query, _batchParams)\n", VertxTypes.repositoryHelper)
            } else {
                b.addCode("%T.batchCompletionStage(%N, this._vertxConnectionFactory.telemetry(), _query, _batchParams)\n", VertxTypes.repositoryHelper, connectionParameter)
            }
            if (function.returnType == resolver.builtIns.unitType) {
                b.addCode("  .thenApply {}\n")
            }
        } else if (isFlow) {
            b.addCode("return ")
            if (connectionParameter == null) {
                b.addCode("%T.Flux.flux(this._vertxConnectionFactory, _query, _tuple, %N).%M()\n", VertxTypes.repositoryHelper, resultMapperName, asFlow)
            } else {
                b.addCode("%T.Flux.flux(%N, this._vertxConnectionFactory.telemetry(), _query, _tuple, %N).%M()\n", VertxTypes.repositoryHelper, connectionParameter, resultMapperName, asFlow)
            }
            return b.build()
        } else {
            b.addCode("var _future: %T = ", CompletableFuture::class.asClassName().parameterizedBy(function.returnType!!.toTypeName()))
            if (connectionParameter == null) {
                b.addCode("%T.completionStage(this._vertxConnectionFactory, _query, _tuple", VertxTypes.repositoryHelper)
            } else {
                b.addCode("%T.completionStage(%N, this._vertxConnectionFactory.telemetry(), _query, _tuple", VertxTypes.repositoryHelper, connectionParameter)
            }

            if (function.returnType == resolver.builtIns.unitType) {
                b.addCode(") {}\n")
            } else if (function.returnType?.toTypeName() == updateCount) {
                b.addCode(") { %T.extractUpdateCount(it) }\n", VertxTypes.rowSetMapper)
            } else {
                b.addCode(", %N)\n", resultMapperName)
            }

        }
        if (isSuspend) {
            b.addCode("return _future.%M()", CommonClassNames.await)
        } else if (!isFlow) {
            b.addCode("return _future.toCompletableFuture().join()")
            if (function.returnType!!.isMarkedNullable) {
                b.addCode("!!")
            }
        }
        return b.build()
    }

    private fun parseResultMapper(method: KSFunctionDeclaration, parameters: List<QueryParameter>, methodType: KSFunction): Mapper? {
        val returnType = methodType.returnType!!
        val returnTypeName = returnType.toTypeName().copy(false)
        if (returnTypeName == UNIT) {
            return null
        }
        if (returnTypeName == updateCount) {
            return null
        }

        for (parameter in parameters) {
            if (parameter is QueryParameter.BatchParameter) {
                throw ProcessingErrorException("@Batch method can't return arbitrary values, it can only return: void/UpdateCount", method)
            }
        }

        val mapperName = method.resultMapperName()
        val mappings = method.parseMappingData()
        val resultSetMapper = mappings.getMapping(VertxTypes.rowSetMapper)
        val rowMapper = mappings.getMapping(VertxTypes.rowMapper)
        if (returnType.isFlow()) {
            val flowParam = returnType.arguments[0]
            val flowReturnTypeName = flowParam.toTypeName().copy(false)
            val mapperType = VertxTypes.rowMapper.parameterizedBy(flowReturnTypeName)
            if (rowMapper != null) {
                return Mapper(rowMapper, mapperType, mapperName)
            }
            return Mapper(mapperType, mapperName)
        }
        val mapperType = VertxTypes.rowSetMapper.parameterizedBy(returnTypeName)
        if (resultSetMapper != null) {
            return Mapper(resultSetMapper, mapperType, mapperName)
        }

        if (rowMapper != null) {
            return if (returnType.isList()) {
                Mapper(rowMapper, mapperType, mapperName) {
                    CodeBlock.of("%T.listRowSetMapper(%L)", VertxTypes.rowSetMapper, it)
                }
            } else {
                Mapper(rowMapper, mapperType, mapperName) {
                    CodeBlock.of("%T.singleRowSetMapper(%L)", VertxTypes.rowSetMapper, it)
                }
            }
        }

        return Mapper(mapperType, mapperName)
    }

    private fun enrichWithExecutor(repositoryElement: KSClassDeclaration, builder: TypeSpec.Builder, constructorBuilder: FunSpec.Builder) {
        builder.addProperty("_vertxConnectionFactory", VertxTypes.connectionFactory, KModifier.PRIVATE)
        builder.addSuperinterface(VertxTypes.repository)
        builder.addFunction(
            FunSpec.builder("getVertxConnectionFactory")
                .addModifiers(KModifier.OVERRIDE)
                .returns(VertxTypes.connectionFactory)
                .addStatement("return this._vertxConnectionFactory")
                .build()
        )
        val executorTag = repositoryElement.parseExecutorTag()
        if (executorTag != null) {
            constructorBuilder.addParameter(
                ParameterSpec.builder("_vertxConnectionFactory", VertxTypes.connectionFactory).addAnnotation(
                    AnnotationSpec.builder(CommonClassNames.tag).addMember("value = %L", executorTag).build()
                ).build()
            )
        } else {
            constructorBuilder.addParameter("_vertxConnectionFactory", VertxTypes.connectionFactory)
        }
        constructorBuilder.addStatement("this._vertxConnectionFactory = _vertxConnectionFactory")
    }
}
