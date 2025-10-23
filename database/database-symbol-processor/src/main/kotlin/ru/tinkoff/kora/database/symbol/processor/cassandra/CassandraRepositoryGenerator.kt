package ru.tinkoff.kora.database.symbol.processor.cassandra

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunction
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.database.symbol.processor.DbUtils
import ru.tinkoff.kora.database.symbol.processor.DbUtils.addMapper
import ru.tinkoff.kora.database.symbol.processor.DbUtils.findQueryMethods
import ru.tinkoff.kora.database.symbol.processor.DbUtils.operationName
import ru.tinkoff.kora.database.symbol.processor.DbUtils.parseExecutorTag
import ru.tinkoff.kora.database.symbol.processor.DbUtils.queryMethodBuilder
import ru.tinkoff.kora.database.symbol.processor.DbUtils.resultMapperName
import ru.tinkoff.kora.database.symbol.processor.Mapper
import ru.tinkoff.kora.database.symbol.processor.QueryWithParameters
import ru.tinkoff.kora.database.symbol.processor.RepositoryGenerator
import ru.tinkoff.kora.database.symbol.processor.cassandra.StatementSetterGenerator.setPreparedStatementParams
import ru.tinkoff.kora.database.symbol.processor.model.QueryParameter
import ru.tinkoff.kora.database.symbol.processor.model.QueryParameterParser
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValue
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.CommonClassNames.await
import ru.tinkoff.kora.ksp.common.CommonClassNames.context
import ru.tinkoff.kora.ksp.common.CommonClassNames.isFlow
import ru.tinkoff.kora.ksp.common.CommonClassNames.isList
import ru.tinkoff.kora.ksp.common.FieldFactory
import ru.tinkoff.kora.ksp.common.FunctionUtils.isSuspend
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.nextControlFlow
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.observe
import ru.tinkoff.kora.ksp.common.parseMappingData


class CassandraRepositoryGenerator(private val resolver: Resolver) : RepositoryGenerator {
    private val repositoryInterface = resolver.getClassDeclarationByName(resolver.getKSNameFromString(CassandraTypes.repository.canonicalName))?.asStarProjectedType()

    override fun generate(repositoryType: KSClassDeclaration, typeBuilder: TypeSpec.Builder, constructorBuilder: FunSpec.Builder): TypeSpec {
        this.enrichWithExecutor(repositoryType, typeBuilder, constructorBuilder)
        val repositoryResolvedType = repositoryType.asStarProjectedType()
        val resultMappers = FieldFactory(typeBuilder, constructorBuilder, "_result_mapper_")
        val parameterMappers = FieldFactory(typeBuilder, constructorBuilder, "_parameter_mapper")

        var methodCounter = 1
        for (method in repositoryType.findQueryMethods()) {
            val methodType = method.asMemberOf(repositoryResolvedType)
            val parameters = QueryParameterParser.parse(CassandraTypes.connection, CassandraTypes.parameterColumnMapper, method, methodType)
            val queryAnnotation = method.findAnnotation(DbUtils.queryAnnotation)!!
            val queryString = queryAnnotation.findValue<String>("value")!!
            val query = QueryWithParameters.parse(queryString, parameters, method)
            val resultMapper = this.parseResultMapper(method, parameters, methodType)?.let { resultMappers.addMapper(it) }
            DbUtils.parseParameterMappers(method, parameters, query, CassandraTypes.parameterColumnMapper) { CassandraNativeTypes.findNativeType(it.toTypeName()) != null }
                .forEach { parameterMappers.addMapper(it) }
            val methodSpec = this.generate(typeBuilder, methodCounter, method, methodType, query, parameters, resultMapper, parameterMappers)
            typeBuilder.addFunction(methodSpec)
            methodCounter++
        }

        return typeBuilder.primaryConstructor(constructorBuilder.build()).build()
    }

    private data class QueryReplace(val index: Int, val name: String)

    private fun generate(
        typeBuilder: TypeSpec.Builder,
        methodNumber: Int,
        funDeclaration: KSFunctionDeclaration,
        function: KSFunction,
        query: QueryWithParameters,
        parameters: List<QueryParameter>,
        resultMapper: String?,
        parameterMappers: FieldFactory
    ): FunSpec {
        var sql = query.rawQuery

        val replaceParams = query.parameters
            .flatMap { p -> p.queryIndexes.map { i -> QueryReplace(i.start, p.sqlParameterName) } }
            .sortedBy { it.index }
            .toList()
        var sqlIndexDiff = 0
        for (parameter in replaceParams) {
            val queryIndexAdjusted: Int = parameter.index - sqlIndexDiff
            sql = sql.substring(0, queryIndexAdjusted) + "?" + sql.substring(queryIndexAdjusted + parameter.name.length + 1)
            sqlIndexDiff += parameter.name.length
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
        val profile = funDeclaration.findAnnotation(CassandraTypes.cassandraProfileAnnotation)?.findValue<String>("value")
        val returnType = function.returnType!!
        val isSuspend = funDeclaration.isSuspend()

        b.addStatement("val _observation = this._cassandraConnectionFactory.telemetry().observe(_query)", context)
        b.addStatement("val _session = this._cassandraConnectionFactory.currentSession()")
        b.addStatement("_observation.observeConnection()")
        if (isSuspend) {
            val code = CodeBlock.builder()
            code.controlFlow("try") {
                addStatement("val _st = _session.prepareAsync(_query.sql()).%M()", await)
                addStatement("var _stmt = _st.boundStatementBuilder()")
                if (profile != null) {
                    addStatement("_stmt.setExecutionProfileName(%S)", profile)
                }
                setPreparedStatementParams(query, parameters, batchParam, parameterMappers)
                addStatement("_observation.observeStatement()")
                addStatement("val _rrs = _session.executeAsync(_s).%M()", await)
                if (returnType != resolver.builtIns.unitType) {
                    if (function.returnType!!.isMarkedNullable) {
                        addStatement("val _result = (%N as %T).apply(_rrs).%M()", resultMapper!!, CassandraTypes.asyncResultSetMapper.parameterizedBy(function.returnType!!.toTypeName()), await)
                    } else {
                        addStatement("val _result = %N.apply(_rrs).%M()", resultMapper!!, await)
                    }
                } else {
                    addStatement("val _result = %T", UNIT)
                }
                addStatement("return _result")
                nextControlFlow("catch (_e: Exception)") {
                    addStatement("_observation.observeError(_e)")
                    addStatement("throw _e")
                }
                nextControlFlow("finally") {
                    addStatement("_observation.end()")
                }
            }
            b.addCode(code.build())
        } else {
            b.addCode("return ")
            b.observe("_observation", returnType.toTypeName()) {
                addStatement("var _stmt = _session.prepare(_query.sql()).boundStatementBuilder()")
                if (profile != null) {
                    addStatement("_stmt.setExecutionProfileName(%S)", profile)
                }
                setPreparedStatementParams(query, parameters, batchParam, parameterMappers)
                addStatement("_observation.observeStatement()")
                controlFlow("try") {
                    addStatement("val _rs = _session.execute(_s)")
                    if (returnType == resolver.builtIns.unitType) {
                    } else {
                        addStatement("val _result = %N.apply(_rs)", resultMapper!!)
                        add("_result")
                        if (!function.returnType!!.isMarkedNullable) {
                            add("!!")
                        }
                        add("\n")
                    }
                    nextControlFlow("catch (_e: Exception)") {
                        addStatement("_observation.observeError(_e)")
                        addStatement("throw _e")
                    }
                    nextControlFlow("finally") {
                        addStatement("_observation.end()")
                    }
                }
            }
        }
        return b.build()
    }

    private fun parseResultMapper(method: KSFunctionDeclaration, parameters: List<QueryParameter>, methodType: KSFunction): Mapper? {
        for (parameter in parameters) {
            if (parameter is QueryParameter.BatchParameter) {
                return null
            }
        }
        val returnType = methodType.returnType!!
        val mapperName = method.resultMapperName()
        val mappings = method.parseMappingData()
        val resultSetMapper = mappings.getMapping(CassandraTypes.resultSetMapper)
        val rowMapper = mappings.getMapping(CassandraTypes.rowMapper)
        if (method.modifiers.contains(Modifier.SUSPEND)) {
            val returnTypeName = returnType.toTypeName().copy(false)
            val mapperType = CassandraTypes.asyncResultSetMapper.parameterizedBy(returnTypeName)
            if (rowMapper != null) {
                if (returnType.isList()) {
                    return Mapper(rowMapper, mapperType, mapperName) {
                        CodeBlock.of("%T.list(%L)", CassandraTypes.asyncResultSetMapper, it)
                    }
                } else {
                    return Mapper(rowMapper, mapperType, mapperName) {
                        CodeBlock.of("%T.one(%L)", CassandraTypes.asyncResultSetMapper, it)
                    }
                }
            }
            if (returnType == resolver.builtIns.unitType) {
                return null
            }
            return Mapper(mapperType, mapperName)
        }
        if (returnType.isFlow()) {
            val flowParam = returnType.arguments[0]
            val returnTypeName = flowParam.toTypeName().copy(false)
            val mapperType = CassandraTypes.rowMapper.parameterizedBy(returnTypeName)
            if (rowMapper != null) {
                return Mapper(rowMapper, mapperType, mapperName)
            }
            return Mapper(mapperType, mapperName)
        }
        val mapperType = CassandraTypes.resultSetMapper.parameterizedBy(returnType.toTypeName())
        if (resultSetMapper != null) {
            return Mapper(resultSetMapper, mapperType, mapperName)
        }
        if (rowMapper != null) {
            if (returnType.isList()) {
                return Mapper(rowMapper, mapperType, mapperName) {
                    CodeBlock.of("%T.listResultSetMapper(%L)", CassandraTypes.resultSetMapper, it)
                }
            } else {
                return Mapper(rowMapper, mapperType, mapperName) {
                    CodeBlock.of("%T.singleResultSetMapper(%L)", CassandraTypes.resultSetMapper, it)
                }
            }
        }
        if (returnType == resolver.builtIns.unitType) {
            return null
        }
        return Mapper(CassandraTypes.resultSetMapper.parameterizedBy(returnType.toTypeName().copy(false)), mapperName)
    }

    override fun repositoryInterface() = repositoryInterface


    private fun enrichWithExecutor(repositoryElement: KSClassDeclaration, builder: TypeSpec.Builder, constructorBuilder: FunSpec.Builder) {
        builder.addProperty("_cassandraConnectionFactory", CassandraTypes.connectionFactory, KModifier.PRIVATE)
        builder.addSuperinterface(CassandraTypes.repository)
        builder.addFunction(
            FunSpec.builder("getCassandraConnectionFactory")
                .addModifiers(KModifier.OVERRIDE)
                .returns(CassandraTypes.connectionFactory)
                .addStatement("return this._cassandraConnectionFactory")
                .build()
        )
        val executorTag = repositoryElement.parseExecutorTag()
        if (executorTag != null) {
            constructorBuilder.addParameter(
                ParameterSpec.builder("_cassandraConnectionFactory", CassandraTypes.connectionFactory).addAnnotation(
                    AnnotationSpec.builder(CommonClassNames.tag).addMember("value = %L", executorTag).build()
                ).build()
            )
        } else {
            constructorBuilder.addParameter("_cassandraConnectionFactory", CassandraTypes.connectionFactory)
        }
        constructorBuilder.addStatement("this._cassandraConnectionFactory = _cassandraConnectionFactory")
    }

}
