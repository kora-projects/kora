package ru.tinkoff.kora.database.symbol.processor.r2dbc

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
import ru.tinkoff.kora.database.symbol.processor.DbUtils.awaitSingle
import ru.tinkoff.kora.database.symbol.processor.DbUtils.awaitSingleOrNull
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
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.FieldFactory
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFlow
import ru.tinkoff.kora.ksp.common.FunctionUtils.isList
import ru.tinkoff.kora.ksp.common.FunctionUtils.isSuspend
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.ksp.common.parseMappingData


class R2DbcRepositoryGenerator(val resolver: Resolver) : RepositoryGenerator {
    private val repositoryInterface = resolver.getClassDeclarationByName(resolver.getKSNameFromString(R2dbcTypes.repository.canonicalName))?.asStarProjectedType()

    override fun generate(repositoryType: KSClassDeclaration, typeBuilder: TypeSpec.Builder, constructorBuilder: FunSpec.Builder): TypeSpec {
        this.enrichWithExecutor(repositoryType, typeBuilder, constructorBuilder)
        val repositoryResolvedType = repositoryType.asStarProjectedType()
        val resultMappers = FieldFactory(typeBuilder, constructorBuilder, "_result_mapper_")
        val parameterMappers = FieldFactory(typeBuilder, constructorBuilder, "_parameter_mapper_")

        var methodCounter = 1
        for (method in repositoryType.findQueryMethods()) {
            val methodType = method.asMemberOf(repositoryResolvedType)
            val parameters = QueryParameterParser.parse(R2dbcTypes.connection, R2dbcTypes.parameterColumnMapper, method, methodType)
            val queryAnnotation = method.findAnnotation(DbUtils.queryAnnotation)!!
            val queryString = queryAnnotation.findValue<String>("value")!!
            val query = QueryWithParameters.parse(queryString, parameters, method)
            val resultMapper = this.parseResultMapper(method, parameters, methodType)?.let { resultMappers.addMapper(it) }
            DbUtils.parseParameterMappers(method, parameters, query, R2dbcTypes.parameterColumnMapper) { R2dbcNativeTypes.findNativeType(it.toTypeName()) != null }
                .forEach { parameterMappers.addMapper(it) }
            val methodSpec = this.generate(typeBuilder, methodCounter, method, methodType, query, parameters, resultMapper, parameterMappers)
            typeBuilder.addFunction(methodSpec)
            methodCounter++
        }

        return typeBuilder.primaryConstructor(constructorBuilder.build()).build()
    }

    private data class QueryReplace(val index: Int, val sqlIndex: Int, val name: String)

    private fun generate(
        typeBuilder: TypeSpec.Builder,
        methodNumber: Int,
        method: KSFunctionDeclaration,
        function: KSFunction,
        query: QueryWithParameters,
        parameters: List<QueryParameter>,
        resultMapperName: String?,
        parameterMappers: FieldFactory
    ): FunSpec {
        var sql = query.rawQuery

        val replaceParams = query.parameters.asSequence()
            .flatMap { p ->
                val replaces = mutableListOf<QueryReplace>()
                for (i in p.queryIndexes.indices) {
                    val queryIndex = p.queryIndexes.get(i)
                    val sqlIndex = p.sqlIndexes.get(i)
                    replaces.add(QueryReplace(queryIndex.start, sqlIndex, p.sqlParameterName))
                }
                replaces.asSequence()
            }
            .sortedBy { it.index }
            .toList()
        var sqlIndexDiff = 0
        for (parameter in replaceParams) {
            val queryIndexAdjusted: Int = parameter.index - sqlIndexDiff
            val index: Int = parameter.sqlIndex + 1
            sql = sql.substring(0, queryIndexAdjusted) + "$" + index + sql.substring(queryIndexAdjusted + parameter.name.length + 1)
            sqlIndexDiff += (parameter.name.length - index.toString().length)
        }

        val b = method.queryMethodBuilder(resolver)

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
                    """.trimIndent(), DbUtils.queryContext, query.rawQuery, sql, method.operationName()
                )
                .build()
        )
        b.addStatement("val _query = %L", queryContextFieldName)

        val batchParam = parameters.firstOrNull { it is QueryParameter.BatchParameter }
        val returnType = function.returnType!!
        val isSuspend = method.isSuspend()
        val isFlow = method.isFlow()
        b.addCode("return ")
        b.controlFlow("%T.deferContextual { _reactorCtx ->", if (isFlow) CommonClassNames.flux else CommonClassNames.mono) {
            b.addStatement("val _ctxCurrent = %T.current(_reactorCtx)", CommonClassNames.contextReactor)
            b.addStatement("val _ctxFork = _ctxCurrent.fork()")
            b.addStatement("_ctxFork.inject()")
            b.addStatement("val _telemetry = this._r2dbcConnectionFactory.telemetry().createContext(_ctxFork, _query)")
            b.controlFlow("_r2dbcConnectionFactory.withConnection%L { _con ->", if (isFlow) "Flux" else "") {
                b.addStatement("val _stmt = _con.createStatement(_query.sql())")
                R2dbcStatementSetterGenerator.generate(b, query, parameters, batchParam, parameterMappers)
                b.addStatement("val _flux = %T.from<%T>(_stmt.execute())", CommonClassNames.flux, R2dbcTypes.result)
                if (returnType == resolver.builtIns.unitType) {
                    b.addCode("_flux.flatMap { it.rowsUpdated }.then().thenReturn(Unit)")
                } else if (returnType.toTypeName() == updateCount) {
                    if (isFlow) {
                        b.addCode("_flux.flatMap { it.rowsUpdated }.map { %T(it) }", updateCount)
                    } else {
                        b.addCode("_flux.flatMap { it.rowsUpdated }.reduce(0L) { v1, v2 -> v1 + v2 }.map { %T(it) }", updateCount)
                    }
                } else {
                    b.addCode("%N.apply(_flux)", resultMapperName)
                }
                b.controlFlow(".doOnEach { _s ->") {
                    b.controlFlow("if (_s.isOnComplete)") {
                        b.addStatement("_telemetry.close(null)")
                        b.addStatement("_ctxCurrent.inject()")
                        b.nextControlFlow("else if (_s.isOnError)")
                        b.addStatement("_telemetry.close(_s.throwable)")
                        b.addStatement("_ctxCurrent.inject()")
                    }
                }
            }
        }

        if (isSuspend) {
            if (returnType.isMarkedNullable) {
                b.addCode(".%M()\n", awaitSingleOrNull)
            } else {
                b.addCode(".%M()\n", awaitSingle)
            }
        } else if (isFlow) {
            b.addCode(".%M()\n", asFlow)
        } else {
            if (returnType.isMarkedNullable) {
                b.addCode(".block()")
            } else {
                b.addCode(".block()!!")
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

        val isGeneratedKeys = method.isAnnotationPresent(DbUtils.idAnnotation)
        for (parameter in parameters) {
            if (parameter is QueryParameter.BatchParameter) {
                if (!isGeneratedKeys) {
                    throw ProcessingErrorException("@Batch method can't return arbitrary values, it can only return: void/UpdateCount or database-generated @Id", method)
                }
            }
        }

        val mapperName = method.resultMapperName()
        val mappings = method.parseMappingData()
        val resultSetMapper = mappings.getMapping(R2dbcTypes.resultFluxMapper)
        val rowMapper = mappings.getMapping(R2dbcTypes.rowMapper)
        if (method.isFlow()) {
            val flowParam = returnType.arguments[0]
            val flowReturnTypeName = flowParam.toTypeName().copy(false)
            val mapperType = R2dbcTypes.resultFluxMapper.parameterizedBy(flowReturnTypeName, CommonClassNames.flux.parameterizedBy(flowReturnTypeName))
            if (rowMapper != null) {
                return Mapper(rowMapper, mapperType, mapperName) {
                    CodeBlock.of("%T.flux(%L)", R2dbcTypes.resultFluxMapper, it)
                }
            }
            return Mapper(mapperType, mapperName)
        }

        val mapperType = R2dbcTypes.resultFluxMapper.parameterizedBy(returnTypeName, CommonClassNames.mono.parameterizedBy(returnTypeName))
        if (resultSetMapper != null) {
            return Mapper(resultSetMapper, mapperType, mapperName)
        }

        if (rowMapper != null) {
            return if (method.isList()) {
                Mapper(rowMapper, mapperType, mapperName) {
                    CodeBlock.of("%T.monoList(%L)", R2dbcTypes.resultFluxMapper, it)
                }
            } else {
                Mapper(rowMapper, mapperType, mapperName) {
                    CodeBlock.of("%T.mono(%L)", R2dbcTypes.resultFluxMapper, it)
                }
            }
        }

        return Mapper(mapperType, mapperName)
    }

    override fun repositoryInterface() = repositoryInterface


    private fun enrichWithExecutor(repositoryElement: KSClassDeclaration, builder: TypeSpec.Builder, constructorBuilder: FunSpec.Builder) {
        builder.addProperty("_r2dbcConnectionFactory", R2dbcTypes.connectionFactory, KModifier.PRIVATE)
        builder.addSuperinterface(R2dbcTypes.repository)
        builder.addFunction(
            FunSpec.builder("getR2dbcConnectionFactory")
                .addModifiers(KModifier.OVERRIDE)
                .returns(R2dbcTypes.connectionFactory)
                .addStatement("return this._r2dbcConnectionFactory")
                .build()
        )
        val executorTag = repositoryElement.parseExecutorTag()
        if (executorTag != null) {
            constructorBuilder.addParameter(
                ParameterSpec.builder("_2dbcConnectionFactory", R2dbcTypes.connectionFactory).addAnnotation(
                    AnnotationSpec.builder(CommonClassNames.tag).addMember("value = %L", executorTag).build()
                ).build()
            )
        } else {
            constructorBuilder.addParameter("_r2dbcConnectionFactory", R2dbcTypes.connectionFactory)
        }
        constructorBuilder.addStatement("this._r2dbcConnectionFactory = _r2dbcConnectionFactory")
    }
}
