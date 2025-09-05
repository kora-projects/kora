package ru.tinkoff.kora.http.server.symbol.procesor

import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.http.server.symbol.procesor.HttpServerClassNames.cookie
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
import ru.tinkoff.kora.ksp.common.CommonClassNames.isCollection
import ru.tinkoff.kora.ksp.common.CommonClassNames.isList
import ru.tinkoff.kora.ksp.common.CommonClassNames.isSet
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.KspCommonUtils.findRepeatableAnnotation
import ru.tinkoff.kora.ksp.common.makeTagAnnotationSpec
import ru.tinkoff.kora.ksp.common.parseMappingData
import ru.tinkoff.kora.ksp.common.parseTags
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException


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

        val tags = declaration.parseTags()
        val paramSpecBuilder = ParameterSpec.builder("_controller", parent.toClassName())
        if (tags.isNotEmpty()) {
            paramSpecBuilder.addAnnotation(tags.makeTagAnnotationSpec())
        }

        val funBuilder = FunSpec.builder(funName)
            .returns(httpServerRequestHandler)
            .addParameter(paramSpecBuilder.build())
        if (tags.isNotEmpty()) {
            funBuilder.addAnnotation(tags.makeTagAnnotationSpec())
        }

        val isSuspend = function.modifiers.contains(Modifier.SUSPEND)
        val isBlocking = !isSuspend
        val bodyParams = mutableListOf<KSValueParameter>()
        function.parameters.forEach {
            when {
                it.isAnnotationPresent(query) -> {
                    if (it.type.resolve().isCollection()) {
                        funBuilder.addQueryParameterMapper(it, it.type.resolve().arguments[0].toTypeName())
                    } else {
                        funBuilder.addQueryParameterMapper(it)
                    }
                }

                it.isAnnotationPresent(header) -> {
                    if (it.type.resolve().isCollection()) {
                        funBuilder.addHeaderParameterMapper(it, it.type.resolve().arguments[0].toTypeName())
                    } else {
                        funBuilder.addHeaderParameterMapper(it)
                    }
                }

                it.isAnnotationPresent(path) -> funBuilder.addPathParameterMapper(it)
                it.isAnnotationPresent(cookie) -> funBuilder.addCookieParameterMapper(it)
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
                funBuilder.beginControlFlow("try")
                funBuilder.beginControlFlow("%N.intercept(_ctx, %N) { _ctx, %N ->", interceptorName, requestName, newRequestName)
                requestName = newRequestName
                val builder = ParameterSpec.builder(interceptorName, interceptor.type)
                if (interceptor.tag != null) {
                    builder.addAnnotation(interceptor.tag)
                }
                funBuilder.addParameter(builder.build())
            }

            funBuilder.beginControlFlow("try")

            function.parameters.forEach { funBuilder.generateParameterDeclaration(it, requestName) }

            val params = function.parameters.joinToString(",") { it.name!!.asString() }
            if (isBlocking) {
                funBuilder.controlFlow("_executor.execute(_ctx) {") {
                    for (param in bodyParams) {
                        val paramName = param.name!!.asString()
                        val paramType = param.type.toTypeName()
                        val mapperName = "_${paramName}Mapper"
                        val castedMapperName = httpServerRequestMapper.parameterizedBy(paramType.copy(true))
                        addCode("val %N = ", paramName).check400 {
                            addStatement("(%N as %T).apply(%N)", mapperName, castedMapperName, requestName)
                        }
                        if (!paramType.isNullable) {
                            controlFlow("if (%N == null)", paramName) {
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
                        addCode("val %N = ", paramName).check400 {
                            addStatement("(%N as %T).apply(%N).%M()", mapperName, castedMapperName, requestName, await)
                        }
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

            funBuilder
                .nextControlFlow("catch (_e: Exception)")
                .beginControlFlow("if (_e is %T)", httpServerResponse)
                .addStatement("%T.failedFuture(_e)", CompletableFuture::class)
                .nextControlFlow("else")
                .addStatement("%T.failedFuture(%T.of(400, _e))", CompletableFuture::class, httpServerResponseException)
                .endControlFlow()
                .endControlFlow()
        }

        for (i in interceptors) {
            funBuilder.nextControlFlow("catch (_e: Exception)")
                .addStatement("%T.failedFuture(_e)", CompletableFuture::class)
                .endControlFlow()
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
        param.findAnnotation(cookie)?.let {
            return parseCookieParameter(param, it)
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
            addCode("val %N = ", parameterName).check400 {
                addStatement("%N.read(%M(_request, %S))", readerParameterName, stringExtractor, name)
            }
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
        val supportedTypeExtractor = ExtractorFunctions.header[parameterTypeName]
        if (supportedTypeExtractor != null) {
            addStatement("val %N = %M(_request, %S)", parameterName, supportedTypeExtractor, name)
            return
        }
        if (parameter.type.resolve().isList()) {
            val readerParameterName = "_${parameterName}StringParameterReader"
            if (parameterTypeName.isNullable) {
                val extractor = MemberName("ru.tinkoff.kora.http.server.common.handler.RequestHandlerUtils", "parseOptionalSomeListHeaderParameter")
                addCode("val %N = ", parameterName).check400 {
                    addStatement("%M(_request, %S, %N)", extractor, name, readerParameterName)
                }
            } else {
                val extractor = MemberName("ru.tinkoff.kora.http.server.common.handler.RequestHandlerUtils", "parseSomeListHeaderParameter")
                addCode("val %N = ", parameterName).check400 {
                    addStatement("%M(_request, %S, %N)", extractor, name, readerParameterName)
                }
            }
        } else if (parameter.type.resolve().isSet()) {
            val readerParameterName = "_${parameterName}StringParameterReader"
            if (parameterTypeName.isNullable) {
                val extractor = MemberName("ru.tinkoff.kora.http.server.common.handler.RequestHandlerUtils", "parseOptionalSomeSetHeaderParameter")
                addCode("val %N = ", parameterName).check400 {
                    addStatement("%M(_request, %S, %N)", extractor, name, readerParameterName)
                }
            } else {
                val extractor = MemberName("ru.tinkoff.kora.http.server.common.handler.RequestHandlerUtils", "parseSomeSetHeaderParameter")
                addCode("val %N = ", parameterName).check400 {
                    addStatement("%M(_request, %S, %N)", extractor, name, readerParameterName)
                }
            }
        } else {
            val readerParameterName = "_${parameterName}StringParameterReader"
            if (parameterTypeName.isNullable) {
                val stringExtractor = ExtractorFunctions.header[STRING.copy(true)]!!
                addCode("val %N = ", parameterName).check400 {
                    addStatement("%M(_request, %S)?.let(%N::read)", stringExtractor, name, readerParameterName)
                }
            } else {
                val stringExtractor = ExtractorFunctions.header[STRING]!!
                addCode("val %N = ", parameterName).check400 {
                    addStatement("%N.read(%M(_request, %S))", readerParameterName, stringExtractor, name)
                }
            }
        }
    }

    private fun FunSpec.Builder.parseCookieParameter(parameter: KSValueParameter, annotation: KSAnnotation) {
        val name = annotation.findValueNoDefault<String>("value").let {
            if (it.isNullOrBlank()) {
                parameter.name!!.asString()
            } else {
                it
            }
        }
        val parameterName = parameter.name!!.asString()
        val parameterTypeName = parameter.type.toTypeName()
        val supportedTypeExtractor = ExtractorFunctions.cookie[parameterTypeName]
        if (supportedTypeExtractor != null) {
            addStatement("val %N = %M(_request, %S)", parameterName, supportedTypeExtractor, name)
            return
        }
        if (parameterTypeName.isNullable) {
            val stringExtractor = ExtractorFunctions.cookie[STRING.copy(true)]!!
            val readerParameterName = "_${parameterName}StringParameterReader"
            addCode("val %N = ", parameterName).check400 {
                addStatement("%M(_request, %S)?.let(%N::read)", stringExtractor, name, readerParameterName)
            }
        } else {
            val stringExtractor = ExtractorFunctions.cookie[STRING]!!
            val readerParameterName = "_${parameterName}StringParameterReader"
            addCode("val %N = ", parameterName).check400 {
                addStatement("%N.read(%M(_request, %S))", readerParameterName, stringExtractor, name)
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
        val supportedTypeExtractor = ExtractorFunctions.query[parameterTypeName]
        if (supportedTypeExtractor != null) {
            addStatement("val %N = %M(_request, %S)", parameterName, supportedTypeExtractor, name)
            return
        }
        if (parameter.type.resolve().isList()) {
            val readerParameterName = "_${parameterName}StringParameterReader"
            if (parameterTypeName.isNullable) {
                val extractor = MemberName("ru.tinkoff.kora.http.server.common.handler.RequestHandlerUtils", "parseOptionalSomeListQueryParameter")
                addCode("val %N = ", parameterName).check400 {
                    addStatement("%M(_request, %S, %N)", extractor, name, readerParameterName)
                }
            } else {
                val extractor = MemberName("ru.tinkoff.kora.http.server.common.handler.RequestHandlerUtils", "parseSomeListQueryParameter")
                addCode("val %N = ", parameterName).check400 {
                    addStatement("%M(_request, %S, %N)", extractor, name, readerParameterName)
                }
            }
        } else if (parameter.type.resolve().isSet()) {
            val readerParameterName = "_${parameterName}StringParameterReader"
            if (parameterTypeName.isNullable) {
                val extractor = MemberName("ru.tinkoff.kora.http.server.common.handler.RequestHandlerUtils", "parseOptionalSomeSetQueryParameter")
                addCode("val %N = ", parameterName).check400 {
                    addStatement("%M(_request, %S, %N)", extractor, name, readerParameterName)
                }
            } else {
                val extractor = MemberName("ru.tinkoff.kora.http.server.common.handler.RequestHandlerUtils", "parseSomeSetQueryParameter")
                addCode("val %N = ", parameterName).check400 {
                    addStatement("%M(_request, %S, %N)", extractor, name, readerParameterName)
                }
            }
        } else {
            if (parameterTypeName.isNullable) {
                val stringExtractor = ExtractorFunctions.query[STRING.copy(true)]!!
                val readerParameterName = "_${parameterName}StringParameterReader"
                addCode("val %N = ", parameterName).check400 {
                    addStatement("%M(_request, %S)?.let(%N::read)", stringExtractor, name, readerParameterName)
                }
            } else {
                val stringExtractor = ExtractorFunctions.query[STRING]!!
                val readerParameterName = "_${parameterName}StringParameterReader"
                addCode("val %N = ", parameterName).check400 {
                    addStatement("%N.read(%M(_request, %S))", readerParameterName, stringExtractor, name)
                }
            }
        }
    }

    private fun FunSpec.Builder.check400(callback: (CodeBlock.Builder) -> Unit) {
        controlFlow("try") {
            val b = CodeBlock.builder()
            callback(b)
            addCode(b.build())
            nextControlFlow("catch (_e: %T)", CompletionException::class.asClassName())
            controlFlow("_e.cause?.let") {
                addStatement("if (it is %T) throw it", httpServerResponse)
                addStatement("throw %T.of(400, it)", httpServerResponseException)
            }
            addStatement("throw %T.of(400, _e)", httpServerResponseException)
            nextControlFlow("catch (_e: %T)", ExecutionException::class.asClassName())
            controlFlow("_e.cause?.let") {
                addStatement("if (it is %T) throw it", httpServerResponse)
                addStatement("throw %T.of(400, it)", httpServerResponseException)
            }
            addStatement("throw %T.of(400, _e)", httpServerResponseException)
            nextControlFlow("catch (_e: Exception)")
            controlFlow("if (_e is %T)", httpServerResponse) {
                addStatement("throw _e")
            }
            addStatement("throw %T.of(400, _e)", httpServerResponseException)
        }
    }

    private fun FunSpec.Builder.addQueryParameterMapper(parameter: KSValueParameter) = addStringParameterMapper(ExtractorFunctions.query, parameter, parameter.type.toTypeName())
    private fun FunSpec.Builder.addQueryParameterMapper(parameter: KSValueParameter, parameterTypeName: TypeName) = addStringParameterMapper(ExtractorFunctions.query, parameter, parameterTypeName)
    private fun FunSpec.Builder.addHeaderParameterMapper(parameter: KSValueParameter) = addStringParameterMapper(ExtractorFunctions.header, parameter, parameter.type.toTypeName())
    private fun FunSpec.Builder.addHeaderParameterMapper(parameter: KSValueParameter, parameterTypeName: TypeName) = addStringParameterMapper(ExtractorFunctions.header, parameter, parameterTypeName)
    private fun FunSpec.Builder.addPathParameterMapper(parameter: KSValueParameter) = addStringParameterMapper(ExtractorFunctions.path, parameter, parameter.type.toTypeName())
    private fun FunSpec.Builder.addCookieParameterMapper(parameter: KSValueParameter) = addStringParameterMapper(ExtractorFunctions.cookie, parameter, parameter.type.toTypeName())
    private fun FunSpec.Builder.addRequestParameterMapper(parameter: KSValueParameter, isBlocking: Boolean) {
        val paramName = parameter.name!!.asString()
        val paramType = parameter.type.toTypeName()
        val mapperName = "_${paramName}Mapper"
        val mapping = parameter.parseMappingData().getMapping(httpServerRequestMapper)
        val mappingMapper = mapping?.mapper
        val mapperType = when {
            mappingMapper == null && isBlocking -> httpServerRequestMapper.parameterizedBy(paramType.copy(false))
            mappingMapper == null && !isBlocking -> httpServerRequestMapper.parameterizedBy(CompletionStage::class.asClassName().parameterizedBy(paramType.copy(false)))
            else -> mappingMapper!!.toTypeName()
        }
        val b = ParameterSpec.builder(mapperName, mapperType)
        mapping?.toTagAnnotation()?.let(b::addAnnotation)
        addParameter(b.build())
    }

    private fun FunSpec.Builder.addStringParameterMapper(knownMappers: Map<TypeName, MemberName>, parameter: KSValueParameter, parameterTypeName: TypeName) {
        val parameterName = parameter.name!!.asString()
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


