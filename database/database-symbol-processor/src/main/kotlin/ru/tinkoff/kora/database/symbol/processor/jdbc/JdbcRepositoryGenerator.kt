package ru.tinkoff.kora.database.symbol.processor.jdbc

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunction
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import reactor.core.publisher.Mono
import ru.tinkoff.kora.database.symbol.processor.DbUtils
import ru.tinkoff.kora.database.symbol.processor.DbUtils.addMapper
import ru.tinkoff.kora.database.symbol.processor.DbUtils.awaitSingle
import ru.tinkoff.kora.database.symbol.processor.DbUtils.awaitSingleOrNull
import ru.tinkoff.kora.database.symbol.processor.DbUtils.findQueryMethods
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
import ru.tinkoff.kora.ksp.common.CommonClassNames.isList
import ru.tinkoff.kora.ksp.common.FieldFactory
import ru.tinkoff.kora.ksp.common.FunctionUtils.isSuspend
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.parseMappingData
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

class JdbcRepositoryGenerator(private val resolver: Resolver) : RepositoryGenerator {
    private val repositoryInterface = resolver.getClassDeclarationByName(resolver.getKSNameFromString(JdbcTypes.jdbcRepository.canonicalName))?.asStarProjectedType()
    override fun repositoryInterface() = repositoryInterface

    override fun generate(repositoryType: KSClassDeclaration, typeBuilder: TypeSpec.Builder, constructorBuilder: FunSpec.Builder): TypeSpec {
        val queryMethods = repositoryType.findQueryMethods()
        enrichWithExecutor(repositoryType, typeBuilder, constructorBuilder, queryMethods)
        val repositoryResolvedType = repositoryType.asStarProjectedType()
        val resultMappers = FieldFactory(typeBuilder, constructorBuilder, "_result_mapper_")
        val parameterMappers = FieldFactory(typeBuilder, constructorBuilder, "_parameter_mapper_")
        for (method in queryMethods) {
            val methodType = method.asMemberOf(repositoryResolvedType)
            val parameters = QueryParameterParser.parse(JdbcTypes.connection, JdbcTypes.jdbcParameterColumnMapper, method, methodType)
            val queryAnnotation = method.findAnnotation(DbUtils.queryAnnotation)!!
            val queryString = queryAnnotation.findValue<String>("value")!!
            val query = QueryWithParameters.parse(queryString, parameters)
            val resultMapper = this.parseResultMapper(method, parameters, methodType)?.let { resultMappers.addMapper(it) }
            DbUtils.parseParameterMappers(method, parameters, query, JdbcTypes.jdbcParameterColumnMapper) { JdbcNativeTypes.findNativeType(it.toTypeName()) != null }
                .forEach { parameterMappers.addMapper(it) }
            val methodSpec = this.generate(method, methodType, query, parameters, resultMapper, parameterMappers)
            typeBuilder.addFunction(methodSpec)
        }
        return typeBuilder.primaryConstructor(constructorBuilder.build()).build()
    }

    private fun generate(method: KSFunctionDeclaration, methodType: KSFunction, query: QueryWithParameters, parameters: List<QueryParameter>, resultMapperName: String?, parameterMappers: FieldFactory): FunSpec {
        val batchParam = parameters.firstOrNull { it is QueryParameter.BatchParameter }
        var sql = query.rawQuery
        for (parameter in query.parameters.sortedByDescending { it.sqlParameterName.length }) {
            sql = sql.replace(":${parameter.sqlParameterName}", "?")
        }
        val b = method.queryMethodBuilder(resolver)
        if (method.isSuspend()) {
            b.beginControlFlow("return %T.fromCompletionStage({", Mono::class)
            b.beginControlFlow("%T.supplyAsync({", CompletableFuture::class)
        }
        val returnTypeName = methodType.returnType?.toTypeName()

        val connection = parameters.firstOrNull { it is QueryParameter.ConnectionParameter }
            ?.let { CodeBlock.of("%L", it.variable) } ?: CodeBlock.of("this._jdbcConnectionFactory.currentConnection()")
        b.addStatement("var _conToUse = %L", connection)
        b.addStatement("val _conToClose: %T?", JdbcTypes.connection)
        b.controlFlow("if (_conToUse == null)") {
            addStatement("_conToUse = this._jdbcConnectionFactory.newConnection()")
            addStatement("_conToClose = _conToUse")
            nextControlFlow("else")
            addStatement("_conToClose = null")
        }
        b.addStatement("val _query = %T(%S, %S)", DbUtils.queryContext, query.rawQuery, sql)
        b.addStatement("val _telemetry = this._jdbcConnectionFactory.telemetry().createContext(ru.tinkoff.kora.common.Context.current(), _query)")
        b.controlFlow("try") {
            controlFlow("_conToClose.use") {
                controlFlow("_conToUse!!.prepareStatement(_query.sql()).use { _stmt ->") {
                    StatementSetterGenerator.generate(b, method, query, parameters, batchParam, parameterMappers)
                    if (methodType.returnType!! == resolver.builtIns.unitType) {
                        if (batchParam != null) {
                            addStatement("_stmt.executeBatch()")
                        } else {
                            addStatement("_stmt.execute()")
                            addStatement("_stmt.getUpdateCount()")
                        }
                        addStatement("_telemetry.close(null)")
                    } else if (returnTypeName == updateCount) {
                        if (batchParam != null) {
                            addStatement("val _updateCount = _stmt.executeLargeBatch().sum()")
                        } else {
                            addStatement("val _updateCount = _stmt.executeLargeUpdate()")
                        }
                        addStatement("_telemetry.close(null)")
                        addCode("return")
                        if (method.isSuspend()) {
                            addCode("@supplyAsync")
                        }
                        addCode(" %T(_updateCount)\n", updateCount)
                    } else {
                        controlFlow("_stmt.executeQuery().use { _rs ->") {
                            addStatement("val _result = %N.apply(_rs)", resultMapperName!!)
                            addStatement("_telemetry.close(null)")
                            addCode("return")
                            if (method.isSuspend()) {
                                addCode("@supplyAsync")
                            }
                            addCode(" _result")
                            if (!methodType.returnType!!.isMarkedNullable) {
                                addCode("!!")
                            }
                            addCode("\n")
                        }
                    }
                }
            }
            nextControlFlow("catch (_e: java.sql.SQLException)")
            addStatement("_telemetry.close(_e)")
            addStatement("throw ru.tinkoff.kora.database.jdbc.RuntimeSqlException(_e)")
            nextControlFlow("catch (_e: Exception)")
            addStatement("_telemetry.close(_e)")
            addStatement("throw _e")
        }
        if (method.isSuspend()) {
            b.endControlFlow()
            b.addCode(", this._executor)")
            b.endControlFlow()
            b.addCode(")")
            if (methodType.returnType!!.isMarkedNullable) {
                b.addCode(".%M()", awaitSingleOrNull)
            } else {
                b.addCode(".%M()", awaitSingle)
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
        val resultSetMapper = mappings.getMapping(JdbcTypes.jdbcResultSetMapper)
        val rowMapper = mappings.getMapping(JdbcTypes.jdbcRowMapper)
        val returnTypeName = returnType.toTypeName().copy(false)
        val mapperType = JdbcTypes.jdbcResultSetMapper.parameterizedBy(returnTypeName)
        if (resultSetMapper != null) {
            return Mapper(resultSetMapper, mapperType, mapperName)
        }
        if (rowMapper != null) {
            if (returnType.isList()) {
                return Mapper(rowMapper, mapperType, mapperName) {
                    CodeBlock.of("%T.listResultSetMapper(%L)", JdbcTypes.jdbcResultSetMapper, it)
                }
            } else {
                return Mapper(rowMapper, mapperType, mapperName) {
                    CodeBlock.of("%T.singleResultSetMapper(%L)", JdbcTypes.jdbcResultSetMapper, it)
                }
            }
        }
        if (returnType == resolver.builtIns.unitType) {
            return null
        }
        if (returnTypeName == updateCount) {
            return null
        }
        return Mapper(mapperType, mapperName)
    }

    private fun enrichWithExecutor(repositoryElement: KSClassDeclaration, builder: TypeSpec.Builder, constructorBuilder: FunSpec.Builder, queryMethods: Sequence<KSFunctionDeclaration>) {
        builder.addProperty("_jdbcConnectionFactory", JdbcTypes.connectionFactory, KModifier.PRIVATE)
        builder.addSuperinterface(JdbcTypes.jdbcRepository)
        builder.addFunction(
            FunSpec.builder("getJdbcConnectionFactory")
                .addModifiers(KModifier.OVERRIDE)
                .returns(JdbcTypes.connectionFactory)
                .addStatement("return this._jdbcConnectionFactory")
                .build()
        )
        val executorTag = repositoryElement.parseExecutorTag()
        if (executorTag != null) {
            constructorBuilder.addParameter(
                ParameterSpec.builder("_jdbcConnectionFactory", JdbcTypes.connectionFactory).addAnnotation(
                    AnnotationSpec.builder(CommonClassNames.tag).addMember("value = %L", executorTag).build()
                ).build()
            )
        } else {
            constructorBuilder.addParameter("_jdbcConnectionFactory", JdbcTypes.connectionFactory)
        }
        constructorBuilder.addStatement("this._jdbcConnectionFactory = _jdbcConnectionFactory")

        if (queryMethods.any { it.isSuspend() }) {
            val executor = Executor::class.asClassName()
            builder.addProperty("_executor", executor, KModifier.PRIVATE, KModifier.FINAL)
            constructorBuilder.addStatement("this._executor = _executor")
            if (executorTag != null) {
                constructorBuilder.addParameter(
                    ParameterSpec.builder("_executor", executor).addAnnotation(
                        AnnotationSpec.builder(CommonClassNames.tag).addMember("value = %L", executorTag).build()
                    ).build()
                )
            } else {
                constructorBuilder.addParameter(ParameterSpec.builder("_executor", executor).build())
            }
        }
    }
}
