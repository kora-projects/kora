package ru.tinkoff.kora.database.symbol.processor.jdbc

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunction
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.database.symbol.processor.DbUtils
import ru.tinkoff.kora.database.symbol.processor.DbUtils.addMapper
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
import ru.tinkoff.kora.ksp.common.CommonClassNames.isList
import ru.tinkoff.kora.ksp.common.FieldFactory
import ru.tinkoff.kora.ksp.common.FunctionUtils.isSuspend
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.TagUtils.addTag
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.ksp.common.parseMappingData
import java.sql.Statement
import java.util.concurrent.Executor

class JdbcRepositoryGenerator(private val resolver: Resolver) : RepositoryGenerator {
    private val withContext = MemberName("kotlinx.coroutines", "withContext")
    private val asCoroutineDispatcher = MemberName("kotlinx.coroutines", "asCoroutineDispatcher")
    private val repositoryInterface = resolver.getClassDeclarationByName(resolver.getKSNameFromString(JdbcTypes.jdbcRepository.canonicalName))?.asStarProjectedType()
    override fun repositoryInterface() = repositoryInterface

    override fun generate(repositoryType: KSClassDeclaration, typeBuilder: TypeSpec.Builder, constructorBuilder: FunSpec.Builder): TypeSpec {
        val queryMethods = repositoryType.findQueryMethods()
        enrichWithExecutor(repositoryType, typeBuilder, constructorBuilder, queryMethods)
        val repositoryResolvedType = repositoryType.asStarProjectedType()
        val resultMappers = FieldFactory(typeBuilder, constructorBuilder, "_result_mapper_")
        val parameterMappers = FieldFactory(typeBuilder, constructorBuilder, "_parameter_mapper_")

        var methodCounter = 1
        for (method in queryMethods) {
            val methodType = method.asMemberOf(repositoryResolvedType)
            val parameters = QueryParameterParser.parse(JdbcTypes.connection, JdbcTypes.jdbcParameterColumnMapper, method, methodType)
            val queryAnnotation = method.findAnnotation(DbUtils.queryAnnotation)!!
            val queryString = queryAnnotation.findValue<String>("value")!!
            val query = QueryWithParameters.parse(queryString, parameters, method)
            val resultMapper = this.parseResultMapper(method, parameters, methodType)?.let { resultMappers.addMapper(it) }
            DbUtils.parseParameterMappers(method, parameters, query, JdbcTypes.jdbcParameterColumnMapper) { JdbcNativeTypes.findNativeType(it.toTypeName()) != null }
                .forEach { parameterMappers.addMapper(it) }
            val methodSpec = this.generate(typeBuilder, methodCounter, method, methodType, query, parameters, resultMapper, parameterMappers)
            typeBuilder.addFunction(methodSpec)
            methodCounter++
        }
        return typeBuilder.primaryConstructor(constructorBuilder.build()).build()
    }

    private fun generate(
        typeBuilder: TypeSpec.Builder,
        methodNumber: Int,
        method: KSFunctionDeclaration,
        methodType: KSFunction,
        query: QueryWithParameters,
        parameters: List<QueryParameter>,
        resultMapperName: String?,
        parameterMappers: FieldFactory
    ): FunSpec {
        val batchParam = parameters.firstOrNull { it is QueryParameter.BatchParameter }
        val isGeneratedKeys = method.isAnnotationPresent(DbUtils.idAnnotation)
        var sql = query.rawQuery
        for (parameter in query.parameters.sortedByDescending { it.sqlParameterName.length }) {
            sql = sql.replace(":${parameter.sqlParameterName}", "?")
        }

        val returnTypeName = methodType.returnType?.toTypeName()
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

        val connection = parameters.firstOrNull { it is QueryParameter.ConnectionParameter }
            ?.let { CodeBlock.of("%L", it.variable) } ?: CodeBlock.of("_jdbcConnectionFactory.currentConnection()")

        val b = method.queryMethodBuilder(resolver)
        if (method.isSuspend()) {
            b.addStatement("val _ctxCurrent = %T.current()", CommonClassNames.context)
            b.beginControlFlow("return %M(kotlin.coroutines.coroutineContext + this._executor.%M()) {", withContext, asCoroutineDispatcher)
        }
        b.addStatement("val _query = %L", queryContextFieldName)

        if (method.isSuspend()) {
            b.addStatement("var _conToUse = %L", connection)
            b.addStatement("val _ctxFork = _ctxCurrent.fork()")
            b.addStatement("_ctxFork.inject()")
            b.addStatement("val _telemetry = _jdbcConnectionFactory.telemetry().createContext(_ctxFork, _query)")
        } else {
            b.addStatement("val _ctxCurrent = %T.current()", CommonClassNames.context)
            b.addStatement("val _telemetry = _jdbcConnectionFactory.telemetry().createContext(_ctxCurrent, _query)")
            b.addStatement("var _conToUse = %L", connection)
        }
        b.addStatement("val _conToClose = ")
        b.controlFlow("if (_conToUse == null)") {
            addStatement("_conToUse = _jdbcConnectionFactory.newConnection()")
            addStatement("_conToUse")
            nextControlFlow("else")
            addStatement("null")
        }
        b.controlFlow("try") {
            controlFlow("_conToClose.use") {
                if (isGeneratedKeys)
                    beginControlFlow("_conToUse!!.prepareStatement(_query.sql(), %T.RETURN_GENERATED_KEYS).use { _stmt ->", Statement::class)
                else
                    beginControlFlow("_conToUse!!.prepareStatement(_query.sql()).use { _stmt ->")

                StatementSetterGenerator.generate(b, query, parameters, batchParam, parameterMappers)
                if (methodType.returnType!! == resolver.builtIns.unitType) {
                    if (batchParam != null) {
                        addStatement("_stmt.executeBatch()")
                    } else {
                        addStatement("_stmt.execute()")
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
                        addCode("@withContext")
                    }
                    addCode(" %T(_updateCount)\n", updateCount)
                } else if (isGeneratedKeys) {
                    if (batchParam != null) {
                        addStatement("val _updateCount = _stmt.executeLargeBatch().sum()")
                    } else {
                        addStatement("val _updateCount = _stmt.executeLargeUpdate()")
                    }
                    controlFlow("_stmt.generatedKeys.use { _rs ->") {
                        addStatement("val _result = %N.apply(_rs)", resultMapperName!!)
                        if (!methodType.returnType!!.isMarkedNullable) {
                            addStatement("  ?: throw NullPointerException(%S)", "Result mapping is expected non-null, but was null")
                        }
                        addStatement("_telemetry.close(null)")
                        addCode("return")
                        if (method.isSuspend()) {
                            addCode("@withContext")
                        }
                        addStatement(" _result")
                    }
                } else {
                    controlFlow("_stmt.executeQuery().use { _rs ->") {
                        addStatement("val _result = %N.apply(_rs)", resultMapperName!!)
                        if (!methodType.returnType!!.isMarkedNullable) {
                            addStatement("  ?: throw NullPointerException(%S)", "Result mapping is expected non-null, but was null")
                        }
                        addStatement("_telemetry.close(null)")
                        addCode("return")
                        if (method.isSuspend()) {
                            addCode("@withContext")
                        }
                        addStatement(" _result")
                    }
                }

                endControlFlow()
            }
            nextControlFlow("catch (_e: java.sql.SQLException)")
            addStatement("_telemetry.close(_e)")
            addStatement("throw ru.tinkoff.kora.database.jdbc.RuntimeSqlException(_e)")
            nextControlFlow("catch (_e: Exception)")
            addStatement("_telemetry.close(_e)")
            addStatement("throw _e")
            nextControlFlow("finally")
            addStatement("_ctxCurrent.inject()")
        }
        if (method.isSuspend()) {
            b.endControlFlow()
        }
        return b.build()
    }

    private fun parseResultMapper(method: KSFunctionDeclaration, parameters: List<QueryParameter>, methodType: KSFunction): Mapper? {
        val returnType = methodType.returnType!!
        val returnTypeName = returnType.toTypeName().copy(false)
        if (returnType == resolver.builtIns.unitType) {
            return null
        }
        if (returnTypeName == updateCount) {
            return null
        }

        val isGeneratedKeys = method.isAnnotationPresent(DbUtils.idAnnotation)
        for (parameter in parameters) {
            if (parameter is QueryParameter.BatchParameter) {
                if (IntArray::class.asTypeName() == returnType.toClassName()) {
                    return null
                } else if (LongArray::class.asTypeName() == returnType.toClassName()) {
                    return null
                } else if (!isGeneratedKeys) {
                    throw ProcessingErrorException("@Batch method can't return arbitrary values, it can only return: void/UpdateCount or database-generated @Id", method)
                }
            }
        }

        val mapperName = method.resultMapperName()
        val mappings = method.parseMappingData()
        val resultSetMapper = mappings.getMapping(JdbcTypes.jdbcResultSetMapper)
        val rowMapper = mappings.getMapping(JdbcTypes.jdbcRowMapper)
        val mapperType = JdbcTypes.jdbcResultSetMapper.parameterizedBy(returnTypeName)
        if (resultSetMapper != null) {
            return Mapper(resultSetMapper, mapperType, mapperName)
        }
        if (rowMapper != null) {
            return if (returnType.isList()) {
                Mapper(rowMapper, mapperType, mapperName) {
                    CodeBlock.of("%T.listResultSetMapper(%L)", JdbcTypes.jdbcResultSetMapper, it)
                }
            } else {
                Mapper(rowMapper, mapperType, mapperName) {
                    CodeBlock.of("%T.singleResultSetMapper(%L)", JdbcTypes.jdbcResultSetMapper, it)
                }
            }
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
                    ParameterSpec.builder("_executor", executor)
                        .addAnnotation(
                            AnnotationSpec.builder(CommonClassNames.tag)
                                .addMember("value = %L", executorTag)
                                .build()
                        )
                        .build()
                )
            } else {
                constructorBuilder.addParameter(
                    ParameterSpec.builder("_executor", executor)
                        .addTag(JdbcTypes.jdbcDatabase)
                        .build()
                )
            }
        }
    }
}
