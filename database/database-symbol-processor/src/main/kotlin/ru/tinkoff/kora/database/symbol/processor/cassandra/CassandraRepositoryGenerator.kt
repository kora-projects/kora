package ru.tinkoff.kora.database.symbol.processor.cassandra

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunction
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
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
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.parseMappingData


class CassandraRepositoryGenerator(private val resolver: Resolver) : RepositoryGenerator {
    private val repositoryInterface = resolver.getClassDeclarationByName(resolver.getKSNameFromString(CassandraTypes.repository.canonicalName))?.asStarProjectedType()

    override fun generate(repositoryType: KSClassDeclaration, typeBuilder: TypeSpec.Builder, constructorBuilder: FunSpec.Builder): TypeSpec {
        this.enrichWithExecutor(repositoryType, typeBuilder, constructorBuilder)
        val repositoryResolvedType = repositoryType.asStarProjectedType()
        val resultMappers = FieldFactory(typeBuilder, constructorBuilder, "_result_mapper_")
        val parameterMappers = FieldFactory(typeBuilder, constructorBuilder, "_parameter_mapper")
        for (method in repositoryType.findQueryMethods()) {
            val methodType = method.asMemberOf(repositoryResolvedType)
            val parameters = QueryParameterParser.parse(CassandraTypes.connection, CassandraTypes.parameterColumnMapper, method, methodType)
            val queryAnnotation = method.findAnnotation(DbUtils.queryAnnotation)!!
            val queryString = queryAnnotation.findValue<String>("value")!!
            val query = QueryWithParameters.parse(queryString, parameters)
            val resultMapper = this.parseResultMapper(method, parameters, methodType)?.let { resultMappers.addMapper(it) }
            DbUtils.parseParameterMappers(method, parameters, query, CassandraTypes.parameterColumnMapper) { CassandraNativeTypes.findNativeType(it.toTypeName()) != null }
                .forEach { parameterMappers.addMapper(it) }
            val methodSpec = this.generate(method, methodType, query, parameters, resultMapper, parameterMappers)
            typeBuilder.addFunction(methodSpec)
        }

        return typeBuilder.primaryConstructor(constructorBuilder.build()).build()
    }

    private fun generate(funDeclaration: KSFunctionDeclaration, function: KSFunction, query: QueryWithParameters, parameters: List<QueryParameter>, resultMapper: String?, parameterMappers: FieldFactory): FunSpec {
        var sql = query.rawQuery
        for (parameter in query.parameters.asSequence().sortedByDescending { it.sqlParameterName.length }) {
            sql = sql.replace(":" + parameter.sqlParameterName, "?")
        }
        val b = funDeclaration.queryMethodBuilder(resolver)
        b.addCode("val _query = %T(\n  %S,\n  %S\n,  %S\n)\n", DbUtils.queryContext, query.rawQuery, sql, funDeclaration.operationName())
        val batchParam = parameters.firstOrNull { it is QueryParameter.BatchParameter }
        val profile = funDeclaration.findAnnotation(CassandraTypes.cassandraProfileAnnotation)?.findValue<String>("value")
        val returnType = function.returnType!!
        val isSuspend = funDeclaration.isSuspend()
        val isFlow = funDeclaration.isFlow()
        if (isSuspend || isFlow) {
            b.addCode("return ")
            b.controlFlow("%T.deferContextual { _reactorCtx ->", if (isFlow) Flux::class else Mono::class) {
                b.addStatement("val _telemetry = this._cassandraConnectionFactory.telemetry().createContext(ru.tinkoff.kora.common.Context.Reactor.current(_reactorCtx), _query)")
                b.addStatement("val _session = this._cassandraConnectionFactory.currentSession()")
                b.addCode("%T.fromCompletionStage(_session.prepareAsync(_query.sql()))", Mono::class.java)
                b.controlFlow(".%L { _st ->", if (isSuspend) "flatMap" else "flatMapMany") {
                    b.addStatement("var _stmt = _st.boundStatementBuilder()")
                    if (profile != null) {
                        b.addStatement("_stmt.setExecutionProfileName(%S)", profile)
                    }
                    StatementSetterGenerator.generate(b, query, parameters, batchParam, parameterMappers)
                    b.addStatement("val _rrs = _session.executeReactive(_s)")
                    if (returnType == resolver.builtIns.unitType) {
                        b.addStatement("%T.from(_rrs).then().thenReturn(%T)", Flux::class, Unit::class)
                    } else {
                        b.addCode("%N.apply(_rrs)", resultMapper!!)
                        if (!function.returnType!!.isMarkedNullable) {
                            addCode("!!")
                        }
                        addCode("\n")
                    }
                }
                b.controlFlow(".doOnEach { _s ->") {
                    controlFlow("if (_s.isOnComplete)") {
                        addStatement("_telemetry.close(null)")
                        nextControlFlow("else if (_s.isOnError())")
                        addStatement("_telemetry.close(_s.throwable)")
                    }
                }
            }
            if (isSuspend) {
                if (returnType.isMarkedNullable) {
                    b.addCode(".%M()", awaitSingleOrNull)
                } else {
                    b.addCode(".%M()", awaitSingle)
                }
            } else {
                b.addCode(".%M()", asFlow)
            }
        } else {
            b.addStatement("val _telemetry = this._cassandraConnectionFactory.telemetry().createContext(ru.tinkoff.kora.common.Context.current(), _query)")
            b.addStatement("val _session = this._cassandraConnectionFactory.currentSession()")
            b.addStatement("var _stmt = _session.prepare(_query.sql()).boundStatementBuilder()")
            if (profile != null) {
                b.addStatement("_stmt.setExecutionProfileName(%S)", profile)
            }
            StatementSetterGenerator.generate(b, query, parameters, batchParam, parameterMappers)
            b.controlFlow("try") {
                addStatement("val _rs = _session.execute(_s)")
                if (returnType == resolver.builtIns.unitType) {
                    addStatement("_telemetry.close(null)")
                } else {
                    addStatement("val _result = %N.apply(_rs)", resultMapper!!)
                    addStatement("_telemetry.close(null)")
                    addCode("return _result")
                    if (!function.returnType!!.isMarkedNullable) {
                        addCode("!!")
                    }
                    addCode("\n")
                }
                nextControlFlow("catch (_e: Exception)")
                addStatement("_telemetry.close(_e)")
                addStatement("throw _e")
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
        val reactiveResultSetMapper = mappings.getMapping(CassandraTypes.reactiveResultSetMapper)
        val rowMapper = mappings.getMapping(CassandraTypes.rowMapper)
        if (method.modifiers.contains(Modifier.SUSPEND)) {
            val mono = Mono::class.asClassName()
            val returnTypeName = returnType.toTypeName().copy(false)
            val mapperType = CassandraTypes.reactiveResultSetMapper.parameterizedBy(returnTypeName, mono.parameterizedBy(returnTypeName))
            if (reactiveResultSetMapper != null) {
                return Mapper(reactiveResultSetMapper, mapperType, mapperName)
            }
            if (rowMapper != null) {
                if (returnType.isList()) {
                    return Mapper(rowMapper, mapperType, mapperName) {
                        CodeBlock.of("%T.monoList(%L)", CassandraTypes.reactiveResultSetMapper, it)
                    }
                } else {
                    return Mapper(rowMapper, mapperType, mapperName) {
                        CodeBlock.of("%T.mono(%L)", CassandraTypes.reactiveResultSetMapper, it)
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
            val flux = Flux::class.asClassName()
            val mapperType = CassandraTypes.reactiveResultSetMapper.parameterizedBy(returnTypeName, flux.parameterizedBy(returnTypeName))
            if (reactiveResultSetMapper != null) {
                return Mapper(reactiveResultSetMapper, mapperType, mapperName)
            }
            if (rowMapper != null) {
                return Mapper(rowMapper, mapperType, mapperName) {
                    CodeBlock.of("%T.flux(%L)", CassandraTypes.reactiveResultSetMapper, it)
                }
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
        return Mapper(mapperType, mapperName)
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
