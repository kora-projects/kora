package ru.tinkoff.kora.database.symbol.processor.vertx

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunction
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.database.symbol.processor.DbUtils
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
import ru.tinkoff.kora.ksp.common.CommonClassNames.isFlow
import ru.tinkoff.kora.ksp.common.CommonClassNames.isList
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFlow
import ru.tinkoff.kora.ksp.common.FunctionUtils.isList
import ru.tinkoff.kora.ksp.common.FunctionUtils.isSuspend
import ru.tinkoff.kora.ksp.common.parseMappingData

class VertxRepositoryGenerator(private val resolver: Resolver, private val kspLogger: KSPLogger) : RepositoryGenerator {
    private val repositoryInterface = resolver.getClassDeclarationByName(resolver.getKSNameFromString(VertxTypes.repository.canonicalName))?.asStarProjectedType()
    override fun repositoryInterface() = repositoryInterface

    @OptIn(KspExperimental::class)
    override fun generate(repositoryType: KSClassDeclaration, typeBuilder: TypeSpec.Builder, constructorBuilder: FunSpec.Builder): TypeSpec {
        this.enrichWithExecutor(repositoryType, typeBuilder, constructorBuilder)
        val repositoryResolvedType = repositoryType.asStarProjectedType()
        for (method in repositoryType.findQueryMethods()) {
            val methodType = method.asMemberOf(repositoryResolvedType)
            val parameters = QueryParameterParser.parse(VertxTypes.connection, method, methodType)
            val queryAnnotation = method.findAnnotation(DbUtils.queryAnnotation)!!
            val queryString = queryAnnotation.findValue<String>("value")!!
            val query = QueryWithParameters.parse(queryString, parameters)
            this.parseResultMapper(method, parameters, methodType)?.apply {
                DbUtils.addMappers(typeBuilder, constructorBuilder, listOf(this))
            }
            val parameterMappers = DbUtils.parseParameterMappers(method, parameters, query, VertxTypes.parameterColumnMapper) {
                VertxNativeTypes.findNativeType(it.toTypeName()) != null
            }
            DbUtils.addMappers(typeBuilder, constructorBuilder, parameterMappers)
            val methodSpec = this.generate(method, methodType, query, parameters)
            typeBuilder.addFunction(methodSpec)
        }

        return typeBuilder.primaryConstructor(constructorBuilder.build()).build()
    }

    private fun generate(funDeclaration: KSFunctionDeclaration, function: KSFunction, query: QueryWithParameters, parameters: List<QueryParameter>): FunSpec {
        var sql = query.rawQuery
        query.parameters.indices.asSequence()
            .map { query.parameters[it].sqlParameterName to "$" + (it + 1) }
            .sortedByDescending { it.first.length }
            .forEach { sql = sql.replace("$" + it.first, it.second) }

        val b = funDeclaration.queryMethodBuilder(resolver)
        b.addCode("val _query = %T(\n  %S,\n  %S\n)\n", DbUtils.queryContext, query.rawQuery, sql)
        val batchParam = parameters.firstOrNull { it is QueryParameter.BatchParameter }
        val isSuspend = funDeclaration.isSuspend()
        val isFlow = funDeclaration.isFlow()
        ParametersToTupleBuilder.generate(b, query, funDeclaration, parameters, batchParam)

        b.addCode("return ")
        if (batchParam != null) {
            b.addCode("%T.batch(this._vertxConnectionFactory, _query, _batchParams).thenReturn(%T)\n", VertxTypes.repositoryHelper, Unit::class)
        } else if (isFlow) {
            b.addCode(
                "%T.flux(this._vertxConnectionFactory, _query, _tuple, %N).asFlow()\n",
                VertxTypes.repositoryHelper,
                funDeclaration.resultMapperName()
            )
        } else {
            if (function.returnType == resolver.builtIns.unitType) {
                b.addCode(
                    "%L.mono(this._vertxConnectionFactory, _query, _tuple) {}\n",
                    VertxTypes.repositoryHelper
                )
            } else {
                b.addCode(
                    "%L.mono(this._vertxConnectionFactory, _query, _tuple, %N)\n",
                    VertxTypes.repositoryHelper,
                    funDeclaration.resultMapperName()
                )
            }
        }
        if (isSuspend) {
            if (function.returnType!!.isMarkedNullable) {
                b.addCode("  .awaitSingleOrNull()")
            } else {
                b.addCode("  .awaitSingle()")
            }
        } else if (!isFlow) {
            if (function.returnType!!.isMarkedNullable) {
                b.addCode("  .block()!!")
            } else {
                b.addCode("  .block()")
            }
        }
        return b.build()
    }

    @OptIn(KspExperimental::class)
    private fun parseResultMapper(method: KSFunctionDeclaration, parameters: List<QueryParameter>, methodType: KSFunction): Mapper? {
        for (parameter in parameters) {
            if (parameter is QueryParameter.BatchParameter) {
                return null
            }
        }
        val returnType = methodType.returnType!!
        val mapperName = method.resultMapperName()
        val mappings = method.parseMappingData()
        val resultSetMapper = mappings.getMapping(VertxTypes.rowSetMapper)
        val rowMapper = mappings.getMapping(VertxTypes.rowMapper)
        if (returnType.isFlow()) {
            val flowParam = returnType.arguments[0]
            val returnTypeName = flowParam.toTypeName().copy(false)
            val mapperType = VertxTypes.rowMapper.parameterizedBy(returnTypeName)
            if (rowMapper != null) {
                return Mapper(rowMapper.mapper!!, mapperType, mapperName)
            }
            return Mapper(mapperType, mapperName)
        }
        val mapperType = VertxTypes.rowSetMapper.parameterizedBy(returnType.toTypeName())
        if (resultSetMapper != null) {
            return Mapper(resultSetMapper.mapper!!, mapperType, mapperName)
        }
        if (rowMapper != null) {
            if (returnType.isList()) {
                return Mapper(rowMapper.mapper!!, mapperType, mapperName) {
                    CodeBlock.of("%T.listRowSetMapper(%L)", VertxTypes.rowSetMapper, it)
                }
            } else {
                return Mapper(rowMapper.mapper!!, mapperType, mapperName) {
                    CodeBlock.of("%T.singleRowSetMapper(%L)", VertxTypes.rowSetMapper, it)
                }
            }
        }
        if (returnType == resolver.builtIns.unitType) {
            return null
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
