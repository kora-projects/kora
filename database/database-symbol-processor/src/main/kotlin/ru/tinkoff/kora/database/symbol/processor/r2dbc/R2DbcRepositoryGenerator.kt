package ru.tinkoff.kora.database.symbol.processor.r2dbc

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunction
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import ru.tinkoff.kora.database.symbol.processor.DbUtils
import ru.tinkoff.kora.database.symbol.processor.DbUtils.asFlow
import ru.tinkoff.kora.database.symbol.processor.DbUtils.awaitSingle
import ru.tinkoff.kora.database.symbol.processor.DbUtils.awaitSingleOrNull
import ru.tinkoff.kora.database.symbol.processor.DbUtils.findQueryMethods
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
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFlow
import ru.tinkoff.kora.ksp.common.FunctionUtils.isList
import ru.tinkoff.kora.ksp.common.FunctionUtils.isSuspend
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.parseMappingData


class R2DbcRepositoryGenerator(val resolver: Resolver) : RepositoryGenerator {
    private val repositoryInterface = resolver.getClassDeclarationByName(resolver.getKSNameFromString(R2dbcTypes.repository.canonicalName))?.asStarProjectedType()

    @KspExperimental
    override fun generate(repositoryType: KSClassDeclaration, typeBuilder: TypeSpec.Builder, constructorBuilder: FunSpec.Builder): TypeSpec {
        this.enrichWithExecutor(repositoryType, typeBuilder, constructorBuilder)
        val repositoryResolvedType = repositoryType.asStarProjectedType()
        for (method in repositoryType.findQueryMethods()) {
            val methodType = method.asMemberOf(repositoryResolvedType)
            val parameters = QueryParameterParser.parse(R2dbcTypes.connection, method, methodType)
            val queryAnnotation = method.findAnnotation(DbUtils.queryAnnotation)!!
            val queryString = queryAnnotation.findValue<String>("value")!!
            val query = QueryWithParameters.parse(queryString, parameters)
            this.parseResultMapper(method, parameters, methodType)?.apply {
                DbUtils.addMappers(typeBuilder, constructorBuilder, listOf(this))
            }
            val parameterMappers = DbUtils.parseParameterMappers(method, parameters, query, R2dbcTypes.parameterColumnMapper) {
                R2dbcNativeTypes.findNativeType(it.toTypeName()) != null
            }
            DbUtils.addMappers(typeBuilder, constructorBuilder, parameterMappers)
            val methodSpec = this.generate(method, methodType, query, parameters)
            typeBuilder.addFunction(methodSpec)
        }

        return typeBuilder.primaryConstructor(constructorBuilder.build()).build()
    }

    private fun generate(funDeclaration: KSFunctionDeclaration, function: KSFunction, query: QueryWithParameters, parameters: List<QueryParameter>): FunSpec {
        var sql = query.rawQuery
        for (p in query.parameters.asSequence().withIndex().sortedByDescending { it.value.sqlParameterName.length }) {
            val parameter = p.value
            sql = sql.replace(":" + parameter.sqlParameterName, "$" + (p.index + 1))
        }
        val b = funDeclaration.queryMethodBuilder(resolver)
        b.addCode("val _query = %T(\n  %S,\n  %S\n)\n", DbUtils.queryContext, query.rawQuery, sql)
        val batchParam = parameters.firstOrNull { it is QueryParameter.BatchParameter }
        val returnType = function.returnType!!
        val isSuspend = funDeclaration.isSuspend()
        val isFlow = funDeclaration.isFlow()
        b.addCode("return ")
        b.controlFlow("%T.deferContextual { _reactorCtx ->", if (isFlow) Flux::class else Mono::class) {
            b.addStatement("val _telemetry = this._r2dbcConnectionFactory.telemetry().createContext(ru.tinkoff.kora.common.Context.Reactor.current(_reactorCtx), _query)")
            b.controlFlow("_r2dbcConnectionFactory.withConnection%L { _con ->", if (isFlow) "Flux" else "") {
                b.addStatement("val _stmt = _con.createStatement(_query.sql())")
                R2dbcStatementSetterGenerator.generate(b, funDeclaration, query, parameters, batchParam)
                b.addStatement("val _flux = %T.from<%T>(_stmt.execute())", Flux::class, R2dbcTypes.result)
                if (returnType == resolver.builtIns.unitType) {
                    b.addCode("_flux.flatMap { it.rowsUpdated }.then().thenReturn(Unit)")
                } else {
                    b.addCode("%N.apply(_flux)", funDeclaration.resultMapperName())
                }
                b.controlFlow(".doOnEach { _s ->") {
                    b.controlFlow("if (_s.isOnComplete)") {
                        b.addStatement("_telemetry.close(null)")
                        b.nextControlFlow("else if (_s.isOnError)")
                        b.addStatement("_telemetry.close(_s.throwable)")
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

    @KspExperimental
    private fun parseResultMapper(method: KSFunctionDeclaration, parameters: List<QueryParameter>, methodType: KSFunction): Mapper? {
        for (parameter in parameters) {
            if (parameter is QueryParameter.BatchParameter) {
                return null
            }
        }
        val returnType = methodType.returnType!!
        val mapperName = method.resultMapperName()
        val mappings = method.parseMappingData()
        val resultSetMapper = mappings.getMapping(R2dbcTypes.resultFluxMapper)
        val rowMapper = mappings.getMapping(R2dbcTypes.rowMapper)
        if (method.isFlow()) {
            val flowParam = returnType.arguments[0]
            val returnTypeName = flowParam.toTypeName().copy(false)
            val flux = Flux::class.asClassName()
            val mapperType = R2dbcTypes.resultFluxMapper.parameterizedBy(returnTypeName, flux.parameterizedBy(returnTypeName))
            if (rowMapper != null) {
                return Mapper(rowMapper, mapperType, mapperName) {
                    CodeBlock.of("%T.flux(%L)", R2dbcTypes.resultFluxMapper, it)
                }
            }
            return Mapper(mapperType, mapperName)
        }
        val mono = Mono::class.asClassName()
        val mapperType = R2dbcTypes.resultFluxMapper.parameterizedBy(returnType.toTypeName(), mono.parameterizedBy(returnType.toTypeName()))
        if (resultSetMapper != null) {
            return Mapper(resultSetMapper, mapperType, mapperName)
        }
        if (rowMapper != null) {
            if (method.isList()) {
                return Mapper(rowMapper, mapperType, mapperName) {
                    CodeBlock.of("%T.monoList(%L)", R2dbcTypes.resultFluxMapper, it)
                }
            } else {
                return Mapper(rowMapper, mapperType, mapperName) {
                    CodeBlock.of("%T.mono(%L)", R2dbcTypes.resultFluxMapper, it)
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
