package ru.tinkoff.kora.http.server.symbol.procesor

import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.http.server.symbol.procesor.HttpServerClassNames.header
import ru.tinkoff.kora.http.server.symbol.procesor.HttpServerClassNames.httpRoute
import ru.tinkoff.kora.http.server.symbol.procesor.HttpServerClassNames.httpServerRequest
import ru.tinkoff.kora.http.server.symbol.procesor.HttpServerClassNames.httpServerRequestHandler
import ru.tinkoff.kora.http.server.symbol.procesor.HttpServerClassNames.httpServerRequestHandlerImpl
import ru.tinkoff.kora.http.server.symbol.procesor.HttpServerClassNames.httpServerRequestMapper
import ru.tinkoff.kora.http.server.symbol.procesor.HttpServerClassNames.httpServerResponse
import ru.tinkoff.kora.http.server.symbol.procesor.HttpServerClassNames.httpServerResponseException
import ru.tinkoff.kora.http.server.symbol.procesor.HttpServerClassNames.httpServerResponseMapper
import ru.tinkoff.kora.http.server.symbol.procesor.HttpServerClassNames.interceptWith
import ru.tinkoff.kora.http.server.symbol.procesor.HttpServerClassNames.interceptWithContainer
import ru.tinkoff.kora.http.server.symbol.procesor.HttpServerClassNames.path
import ru.tinkoff.kora.http.server.symbol.procesor.HttpServerClassNames.query
import ru.tinkoff.kora.http.server.symbol.procesor.HttpServerClassNames.stringParameterReader
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.CommonClassNames.await
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.KspCommonUtils.findRepeatableAnnotation
import ru.tinkoff.kora.ksp.common.makeTagAnnotationSpec
import ru.tinkoff.kora.ksp.common.parseMappingData
import java.util.concurrent.CompletionStage


class RouteProcessor {
    private val future = MemberName("kotlinx.coroutines.future", "future")
    private val coroutineScope = MemberName("kotlinx.coroutines", "CoroutineScope")
    private val dispatchers = ClassName("kotlinx.coroutines", "Dispatchers")

    data class Route(val method: String, val pathTemplate: String)

    internal fun buildHttpRouteFunction(declaration: KSClassDeclaration, rootPath: String, function: KSFunctionDeclaration): FunSpec.Builder {
        val requestMappingData = extractRoute(rootPath, function)
        val parent = function.parent as KSClassDeclaration
        val funName = requestMappingData.funName()
        val returnType = function.returnType!!.resolve()
        val returnTypeName = returnType.toTypeName()
        val interceptors = declaration.findRepeatableAnnotation(interceptWith, interceptWithContainer).asSequence()
            .plus(function.findRepeatableAnnotation(interceptWith, interceptWithContainer))
            .map { it.parseInterceptor() }
            .distinct()
            .toList()

        val funBuilder = FunSpec.builder(funName)
            .returns(httpServerRequestHandler)
            .addParameter("_controller", parent.toClassName())
        val isSuspend = function.modifiers.contains(Modifier.SUSPEND)
        val isBlocking = !isSuspend
        val bodyParams = mutableListOf<KSValueParameter>()
        function.parameters.forEach {
            val type = it.type.toTypeName()
            when {
                it.isAnnotationPresent(query) -> funBuilder.addQueryParameterMapper(it)
                it.isAnnotationPresent(path) -> funBuilder.addPathParameterMapper(it)
                it.isAnnotationPresent(header) -> funBuilder.addHeaderParameterMapper(it)
                else -> {
                    val type = it.type.toTypeName()
                    if (type != CommonClassNames.context && type != httpServerRequest) {
                        funBuilder.addRequestParameterMapper(it, isBlocking)
                        bodyParams.add(it)
                    }
                }
            }
        }
        funBuilder.addResponseMapper(function)
        if (isBlocking) {
            funBuilder.addParameter("_executor", HttpServerClassNames.blockingRequestExecutor)
        }

        funBuilder.controlFlow("return %T.of(%S, %S) { _ctx, _request ->", httpServerRequestHandlerImpl, requestMappingData.method, requestMappingData.pathTemplate) {
            var requestName = "_request"
            for (i in interceptors.indices) {
                val interceptor = interceptors[i]
                val interceptorName = "_interceptor" + (i + 1)
                val newRequestName = "_request" + (i + 1)
                funBuilder.beginControlFlow("%N.intercept(_ctx, %N) { _ctx, %N ->", interceptorName, requestName, newRequestName)
                requestName = newRequestName
                val builder = ParameterSpec.builder(interceptorName, interceptor.type)
                if (interceptor.tag != null) {
                    builder.addAnnotation(interceptor.tag)
                }
                funBuilder.addParameter(builder.build())
            }
            function.parameters.forEach { funBuilder.generateParameterDeclaration(it, requestName) }
            val params = function.parameters.joinToString(",") { it.name!!.asString() }
            if (isBlocking) {
                funBuilder.controlFlow("_executor.execute(_ctx) {") {
                    for (param in bodyParams) {
                        val paramName = param.name!!.asString()
                        val paramType = param.type.toTypeName()
                        val mapperName = "_${paramName}Mapper"
                        val castedMapperName = httpServerRequestMapper.parameterizedBy(paramType.copy(true))
                        funBuilder.addStatement("val %N = (%N as %T).apply(%N)", paramName, mapperName, castedMapperName, requestName)
                        if (!paramType.isNullable) {
                            funBuilder.controlFlow("if (%N == null)", paramName) {
                                addStatement("throw %T.of(400, %S)", httpServerResponseException, "Parameter $paramName is not nullable, but got null from mapper")
                            }
                        }
                    }
                    addStatement("val _result = _controller.%L(%L)", function.simpleName.asString(), params)
                    if (returnTypeName == UNIT) {
                        addStatement("return@execute %T.of(200)", httpServerResponse)
                    } else if (returnTypeName == httpServerResponse) {
                        addStatement("return@execute _result")
                    } else {
                        addStatement("return@execute _responseMapper.apply(_ctx, _request, _result)")
                    }
                }
            } else {
                funBuilder.controlFlow("%M(%T.Unconfined + %T.Kotlin.asCoroutineContext(_ctx)).%M {", coroutineScope, dispatchers, CommonClassNames.context, future) {
                    for (param in bodyParams) {
                        val paramName = param.name!!.asString()
                        val paramType = param.type.toTypeName()
                        val mapperName = "_${paramName}Mapper"
                        val castedMapperName = httpServerRequestMapper.parameterizedBy(CompletionStage::class.asClassName().parameterizedBy(paramType.copy(true)))
                        funBuilder.addStatement("val %N = (%N as %T).apply(%N).%M()", paramName, mapperName, castedMapperName, requestName, await)
                        if (!paramType.isNullable) {
                            funBuilder.controlFlow("if (%N == null)", paramName) {
                                addStatement("throw %T.of(400, %S)", httpServerResponseException, "Parameter $paramName is not nullable, but got null from mapper")
                            }
                        }
                    }
                    addStatement("val _result = _controller.%L(%L)", function.simpleName.asString(), params)
                    if (returnTypeName == UNIT) {
                        addStatement("return@future %T.of(200)", httpServerResponse)
                    } else if (returnTypeName == httpServerResponse) {
                        addStatement("return@future _result")
                    } else {
                        addStatement("return@future _responseMapper.apply(_ctx, _request, _result)")
                    }
                }
            }
        }


        for (i in interceptors) {
            funBuilder.endControlFlow()
        }

        return funBuilder
    }

    private fun FunSpec.Builder.generateParameterDeclaration(param: KSValueParameter, requestName: String) {
        param.findAnnotation(query)?.let {
            return parseQueryParameter(param, it)
        }
        param.findAnnotation(header)?.let {
            return parseHeaderParameter(param, it)
        }
        param.findAnnotation(path)?.let {
            return parsePathParameter(param, it)
        }
        val type = param.type.toTypeName()
        if (type == CommonClassNames.context) {
            addStatement("val %N = _ctx", param.name!!.asString())
        }
        if (type == httpServerRequest) {
            addStatement("val %N = %N", param.name!!.asString(), requestName)
        }
    }

    private fun extractRoute(rootPath: String, declaration: KSFunctionDeclaration): Route {
        val httpRoute = declaration.findAnnotation(httpRoute)!!
        val method = httpRoute.findValueNoDefault<String>("method")!!
        val path = httpRoute.findValueNoDefault<String>("path")!!
        return Route(method, "$rootPath${path}")
    }

    private fun FunSpec.Builder.parsePathParameter(parameter: KSValueParameter, annotation: KSAnnotation) {
        val name = annotation.findValueNoDefault<String>("value").let {
            if (it.isNullOrBlank()) {
                parameter.name!!.asString()
            } else {
                it
            }
        }
        val parameterName = parameter.name!!.asString()
        val parameterTypeName = parameter.type.toTypeName()
        val extractor = ExtractorFunctions.path[parameterTypeName]
        if (extractor != null) {
            addStatement("val %N = %M(_request, %S)", parameterName, extractor, name)
        } else {
            val stringExtractor = ExtractorFunctions.path[STRING]!!
            val readerParameterName = "_${parameterName}StringParameterReader"
            addStatement("val %N = %N.read(%M(_request, %S))", parameterName, readerParameterName, stringExtractor, name)
        }
    }

    private fun FunSpec.Builder.parseHeaderParameter(parameter: KSValueParameter, annotation: KSAnnotation) {
        val name = annotation.findValueNoDefault<String>("value").let {
            if (it.isNullOrBlank()) {
                parameter.name!!.asString()
            } else {
                it
            }
        }
        val parameterName = parameter.name!!.asString()
        val parameterTypeName = parameter.type.toTypeName()
        val extractor = ExtractorFunctions.header[parameterTypeName]
        if (extractor != null) {
            addStatement("val %N = %M(_request, %S)", parameterName, extractor, name)
        } else {
            if (parameterTypeName.isNullable) {
                val stringExtractor = ExtractorFunctions.header[STRING.copy(true)]!!
                val readerParameterName = "_${parameterName}StringParameterReader"
                addStatement("val %N = %M(_request, %S)?.let(%N::read)", stringExtractor, name, parameterName, readerParameterName)
            } else {
                val stringExtractor = ExtractorFunctions.header[STRING]!!
                val readerParameterName = "_${parameterName}StringParameterReader"
                addStatement("val %N = %N.read(%M(_request, %S))", parameterName, readerParameterName, stringExtractor, name)
            }
        }
    }

    private fun FunSpec.Builder.parseQueryParameter(parameter: KSValueParameter, annotation: KSAnnotation) {
        val name = annotation.findValueNoDefault<String>("value").let {
            if (it.isNullOrBlank()) {
                parameter.name!!.asString()
            } else {
                it
            }
        }
        val parameterName = parameter.name!!.asString()
        val parameterTypeName = parameter.type.toTypeName()
        val extractor = ExtractorFunctions.query[parameterTypeName]
        if (extractor != null) {
            addStatement("val %N = %M(_request, %S)", parameterName, extractor, name)
        } else {
            if (parameterTypeName.isNullable) {
                val stringExtractor = ExtractorFunctions.query[STRING.copy(true)]!!
                val readerParameterName = "_${parameterName}StringParameterReader"
                addStatement("val %N = %M(_request, %S)?.let(%N::read)", stringExtractor, name, parameterName, readerParameterName)
            } else {
                val stringExtractor = ExtractorFunctions.query[STRING]!!
                val readerParameterName = "_${parameterName}StringParameterReader"
                addStatement("val %N = %N.read(%M(_request, %S))", parameterName, readerParameterName, stringExtractor, name)
            }
        }
    }

    private fun FunSpec.Builder.addQueryParameterMapper(parameter: KSValueParameter) = addStringParameterMapper(ExtractorFunctions.query, parameter)
    private fun FunSpec.Builder.addPathParameterMapper(parameter: KSValueParameter) = addStringParameterMapper(ExtractorFunctions.path, parameter)
    private fun FunSpec.Builder.addHeaderParameterMapper(parameter: KSValueParameter) = addStringParameterMapper(ExtractorFunctions.header, parameter)
    private fun FunSpec.Builder.addRequestParameterMapper(parameter: KSValueParameter, isBlocking: Boolean) {
        val paramName = parameter.name!!.asString()
        val paramType = parameter.type.toTypeName()
        val mapperName = "_${paramName}Mapper"
        if (isBlocking) {
            addParameter(mapperName, httpServerRequestMapper.parameterizedBy(paramType.copy(false)))
        } else {
            addParameter(mapperName, httpServerRequestMapper.parameterizedBy(CompletionStage::class.asClassName().parameterizedBy(paramType.copy(false))))
        }

    }

    private fun FunSpec.Builder.addStringParameterMapper(knownMappers: Map<TypeName, MemberName>, parameter: KSValueParameter) {
        val parameterName = parameter.name!!.asString()
        val parameterTypeName = parameter.type.toTypeName()
        val extractor = knownMappers[parameterTypeName]
        if (extractor == null) {
            val readerParameterName = "_${parameterName}StringParameterReader"
            addParameter(readerParameterName, stringParameterReader.parameterizedBy(parameterTypeName.copy(false)))
        }
    }

    data class Interceptor(val type: TypeName, val tag: AnnotationSpec?)

    private fun KSAnnotation.parseInterceptor(): Interceptor {
        val interceptorType = this.findValueNoDefault<KSType>("value")!!.toTypeName()
        val interceptorTag = findValueNoDefault<KSAnnotation>("tag")
        val interceptorTagAnnotationSpec = if (interceptorTag == null) {
            null
        } else {
            @Suppress("UNCHECKED_CAST")
            val tags = interceptorTag.arguments[0].value!! as List<KSType>
            if (tags.isNotEmpty()) {
                tags.makeTagAnnotationSpec()
            } else {
                null
            }
        }
        return Interceptor(interceptorType, interceptorTagAnnotationSpec)
    }


    private fun Route.funName(): String {
        val suffix = if (pathTemplate.endsWith("/")) "_trailing_slash" else ""
        return method.lowercase() + pathTemplate.split(Regex("[^A-Za-z0-9]+"))
            .filter { it.isNotBlank() }
            .joinToString("_", "_", suffix)
    }


    private fun FunSpec.Builder.addResponseMapper(function: KSFunctionDeclaration) {
        val returnTypeName = function.returnType!!.toTypeName()
        val mapperClassName = httpServerResponseMapper.parameterizedBy(returnTypeName)
        val mapping = function.parseMappingData().getMapping(httpServerResponseMapper)
        if (mapping != null) {
            val responseMapperType = if (mapping.mapper != null) mapping.mapper!!.toTypeName() else mapperClassName
            val b = ParameterSpec.builder("_responseMapper", responseMapperType)
            mapping.toTagAnnotation()?.let {
                b.addAnnotation(it)
            }
            addParameter(b.build())
        } else if (returnTypeName != UNIT && returnTypeName != httpServerResponse) {
            addParameter(ParameterSpec.builder("_responseMapper", mapperClassName).build())
        }
    }
}


