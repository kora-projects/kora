package io.koraframework.http.server.symbol.procesor

import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.http.server.symbol.procesor.HttpServerClassNames.cookie
import io.koraframework.http.server.symbol.procesor.HttpServerClassNames.header
import io.koraframework.http.server.symbol.procesor.HttpServerClassNames.httpRoute
import io.koraframework.http.server.symbol.procesor.HttpServerClassNames.httpServerRequest
import io.koraframework.http.server.symbol.procesor.HttpServerClassNames.httpServerRequestHandler
import io.koraframework.http.server.symbol.procesor.HttpServerClassNames.httpServerRequestHandlerImpl
import io.koraframework.http.server.symbol.procesor.HttpServerClassNames.httpServerRequestMapper
import io.koraframework.http.server.symbol.procesor.HttpServerClassNames.httpServerResponse
import io.koraframework.http.server.symbol.procesor.HttpServerClassNames.httpServerResponseException
import io.koraframework.http.server.symbol.procesor.HttpServerClassNames.httpServerResponseMapper
import io.koraframework.http.server.symbol.procesor.HttpServerClassNames.interceptWith
import io.koraframework.http.server.symbol.procesor.HttpServerClassNames.interceptWithContainer
import io.koraframework.http.server.symbol.procesor.HttpServerClassNames.path
import io.koraframework.http.server.symbol.procesor.HttpServerClassNames.query
import io.koraframework.http.server.symbol.procesor.HttpServerClassNames.stringParameterReader
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.AnnotationUtils.findValueNoDefault
import io.koraframework.ksp.common.CommonClassNames.isCollection
import io.koraframework.ksp.common.CommonClassNames.isList
import io.koraframework.ksp.common.CommonClassNames.isSet
import io.koraframework.ksp.common.FunctionUtils.isSuspend
import io.koraframework.ksp.common.KotlinPoetUtils.controlFlow
import io.koraframework.ksp.common.KspCommonUtils.findRepeatableAnnotation
import io.koraframework.ksp.common.TagUtils.parseTag
import io.koraframework.ksp.common.TagUtils.toTagAnnotation
import io.koraframework.ksp.common.parseMappingData
import io.koraframework.ksp.common.exception.ProcessingErrorException
import java.util.Locale


class RouteProcessor {
    data class Route(val method: String, val pathTemplate: String)

    internal fun buildHttpRouteFunction(declaration: KSClassDeclaration, rootPath: String, function: KSFunctionDeclaration, generatedNames: MutableSet<String>): FunSpec.Builder {
        if (function.isSuspend()) {
            throw ProcessingErrorException(
                """
                HTTP server controller method is invalid:
                  ${function.simpleName.asString()}

                Problem:
                  Suspend methods are not supported by the HTTP server controller generator.

                Hint:
                  Generated HTTP handlers use regular blocking controller signatures. For structured concurrency, enable Java preview features with --enable-preview and use StructuredTaskScope, for example:

                    fun getDashboard(userId: Long): Dashboard =
                        StructuredTaskScope.open(
                            StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow<Any>(),
                        ).use { scope ->
                            val profile = scope.fork(Callable { profileService.getProfile(userId) })
                            val recommendations = scope.fork(Callable { recommendationService.getForUser(userId) })

                            scope.join()

                            Dashboard(profile.get(), recommendations.get())
                        }

                Fix:
                  Remove suspend from the controller method.
                """.trimIndent(),
                function
            )
        }
        val requestMappingData = extractRoute(rootPath, function)
        val funName = requestMappingData.funName(function, generatedNames)
        val returnType = function.returnType!!.resolve()
        val returnTypeName = returnType.toTypeName()
        val interceptors = declaration.findRepeatableAnnotation(interceptWith, interceptWithContainer).asSequence()
            .plus(function.overrideChain().toList().asReversed().flatMap { it.findRepeatableAnnotation(interceptWith, interceptWithContainer) })
            .map { it.parseInterceptor() }
            .distinct()
            .toList()

        val tags = declaration.parseTag()
        val paramSpecBuilder = ParameterSpec.builder("_controller", declaration.toClassName())
        if (tags != null) {
            paramSpecBuilder.addAnnotation(tags.toTagAnnotation())
        }

        val funBuilder = FunSpec.builder(funName)
            .returns(httpServerRequestHandler)
            .addParameter(paramSpecBuilder.build())
        if (tags != null) {
            funBuilder.addAnnotation(tags.toTagAnnotation())
        }

        val bodyParams = mutableListOf<KSValueParameter>()
        function.parameters.forEach {
            when {
                it.findMappingAnnotation(function, query) != null -> {
                    if (it.type.resolve().isCollection()) {
                        funBuilder.addQueryParameterMapper(it, it.type.resolve().arguments[0].toTypeName())
                    } else {
                        funBuilder.addQueryParameterMapper(it)
                    }
                }

                it.findMappingAnnotation(function, header) != null -> {
                    if (it.type.resolve().isCollection()) {
                        funBuilder.addHeaderParameterMapper(it, it.type.resolve().arguments[0].toTypeName())
                    } else {
                        funBuilder.addHeaderParameterMapper(it)
                    }
                }

                it.findMappingAnnotation(function, path) != null -> {
                    val name = it.findMappingAnnotation(function, path)!!.findValueNoDefault<String>("value").let { value ->
                        if (value.isNullOrBlank()) it.name!!.asString() else value
                    }
                    if (!requestMappingData.pathTemplate.contains("{$name}")) {
                        throw ProcessingErrorException("Path parameter '$name' is not present in the request mapping path", it)
                    }
                    funBuilder.addPathParameterMapper(it)
                }

                it.findMappingAnnotation(function, cookie) != null -> funBuilder.addCookieParameterMapper(it)
                else -> {
                    val type = it.type.toTypeName()
                    if (type != httpServerRequest) {
                        funBuilder.addRequestParameterMapper(it)
                        bodyParams.add(it)
                    }
                }
            }
        }
        if (bodyParams.isNotEmpty()) {
            // request mappers are cast to a nullable type argument below
            funBuilder.addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "UNCHECKED_CAST").build())
        }
        funBuilder.addResponseMapper(function)

        val processLabel = if (interceptors.isEmpty()) {
            CodeBlock.of("process@")
        } else {
            CodeBlock.of("")
        }
        funBuilder.controlFlow("return %T.of(%S, %S) %L{ _request ->", httpServerRequestHandlerImpl, requestMappingData.method, requestMappingData.pathTemplate, processLabel) {
            var requestName = "_request"
            for (i in interceptors.indices) {
                val interceptor = interceptors[i]
                val interceptorName = "_interceptor" + (i + 1)
                val newRequestName = "_request" + (i + 1)
                val label = if (i == interceptors.size - 1) {
                    CodeBlock.of("process@")
                } else {
                    CodeBlock.of("")
                }

                funBuilder.beginControlFlow("%N.intercept(%N) %L{ %N ->", interceptorName, requestName, label, newRequestName)
                requestName = newRequestName
                val builder = ParameterSpec.builder(interceptorName, interceptor.type)
                if (interceptor.tag != null) {
                    builder.addAnnotation(interceptor.tag)
                }
                funBuilder.addParameter(builder.build())
            }

            function.parameters.forEach { funBuilder.generateParameterDeclaration(function, it, requestName) }

            val params = function.parameters.joinToString(",") { it.name!!.asString() }
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
                addStatement("return@process %T.of(200)", httpServerResponse)
            } else if (returnTypeName.copy(nullable = false) == httpServerResponse) {
                addStatement("return@process _result")
            } else {
                addStatement("return@process _responseMapper.apply(%N, _result)", requestName)
            }

        }

        for (i in interceptors) {
            funBuilder.endControlFlow()
        }

        return funBuilder
    }

    private fun FunSpec.Builder.generateParameterDeclaration(function: KSFunctionDeclaration, param: KSValueParameter, requestName: String) {
        param.findMappingAnnotation(function, query)?.let {
            return parseQueryParameter(param, it, requestName)
        }
        param.findMappingAnnotation(function, header)?.let {
            return parseHeaderParameter(param, it, requestName)
        }
        param.findMappingAnnotation(function, path)?.let {
            return parsePathParameter(param, it, requestName)
        }
        param.findMappingAnnotation(function, cookie)?.let {
            return parseCookieParameter(param, it, requestName)
        }
        val type = param.type.toTypeName()
        if (type == httpServerRequest) {
            addStatement("val %N = %N", param.name!!.asString(), requestName)
        }
    }

    private fun extractRoute(rootPath: String, declaration: KSFunctionDeclaration): Route {
        val httpRoute = declaration.findHttpRoute()!!
        val method = httpRoute.findValueNoDefault<String>("method")!!
        var path = httpRoute.findValueNoDefault<String>("path")!!
        if (path.isNotEmpty() && !path.startsWith("/")) {
            path = "/$path"
        }
        var controllerPath = rootPath
        if (controllerPath.isNotEmpty() && !controllerPath.startsWith("/")) {
            controllerPath = "/$controllerPath"
        }
        controllerPath = controllerPath.removeSuffix("/")
        val finalPath = "$controllerPath$path".ifEmpty { "/" }
        validateWildcard(finalPath, declaration)
        return Route(method.uppercase(Locale.ROOT), finalPath)
    }

    private fun validateWildcard(path: String, declaration: KSFunctionDeclaration) {
        val wildcard = path.indexOf('*')
        if (wildcard < 0) {
            return
        }

        val previousClosingBrace = path.lastIndexOf('}', wildcard)
        val insideParameter = path.lastIndexOf('{', wildcard) > previousClosingBrace
        val multipleWildcards = path.indexOf('*', wildcard + 1) >= 0
        val outsideFinalSegment = wildcard < path.lastIndexOf('/')
        if (insideParameter || multipleWildcards || outsideFinalSegment) {
            throw ProcessingErrorException(
                """
                HTTP server route path is invalid:
                  $path

                Problem:
                  Wildcard '*' is only allowed once and only in the final path segment.

                Hint:
                  Valid examples are '/files/*' and '/files/*.js'. Wildcards inside path parameters, multiple wildcards, or wildcards before another '/' are not supported.

                Fix:
                  Move '*' to the last path segment, remove extra wildcards, or replace it with a named @Path parameter.
                """.trimIndent(),
                declaration
            )
        }
    }

    private fun FunSpec.Builder.parsePathParameter(parameter: KSValueParameter, annotation: KSAnnotation, requestName: String) {
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
            addStatement("val %N = %M(%N, %S)", parameterName, extractor, requestName, name)
        } else {
            val stringExtractor = ExtractorFunctions.path[STRING]!!
            val readerParameterName = "_${parameterName}StringParameterReader"
            addCode("val %N = ", parameterName).check400 {
                addStatement("%N.read(%M(%N, %S))", readerParameterName, stringExtractor, requestName, name)
            }
        }
    }

    private fun FunSpec.Builder.parseHeaderParameter(parameter: KSValueParameter, annotation: KSAnnotation, requestName: String) {
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
            addStatement("val %N = %M(%N, %S)", parameterName, supportedTypeExtractor, requestName, name)
            return
        }
        if (parameter.type.resolve().isList()) {
            val readerParameterName = "_${parameterName}StringParameterReader"
            if (parameterTypeName.isNullable) {
                val extractor = MemberName("io.koraframework.http.server.common.request.HttpRequestHandlerUtils", "parseHeaderSomeListNullable")
                addCode("val %N = ", parameterName).check400 {
                    addStatement("%M(%N, %S, %N)", extractor, requestName, name, readerParameterName)
                }
            } else {
                val extractor = MemberName("io.koraframework.http.server.common.request.HttpRequestHandlerUtils", "parseHeaderSomeList")
                addCode("val %N = ", parameterName).check400 {
                    addStatement("%M(%N, %S, %N)", extractor, requestName, name, readerParameterName)
                }
            }
        } else if (parameter.type.resolve().isSet()) {
            val readerParameterName = "_${parameterName}StringParameterReader"
            if (parameterTypeName.isNullable) {
                val extractor = MemberName("io.koraframework.http.server.common.request.HttpRequestHandlerUtils", "parseHeaderSomeSetNullable")
                addCode("val %N = ", parameterName).check400 {
                    addStatement("%M(%N, %S, %N)", extractor, requestName, name, readerParameterName)
                }
            } else {
                val extractor = MemberName("io.koraframework.http.server.common.request.HttpRequestHandlerUtils", "parseHeaderSomeSet")
                addCode("val %N = ", parameterName).check400 {
                    addStatement("%M(%N, %S, %N)", extractor, requestName, name, readerParameterName)
                }
            }
        } else {
            val readerParameterName = "_${parameterName}StringParameterReader"
            if (parameterTypeName.isNullable) {
                val stringExtractor = ExtractorFunctions.header[STRING.copy(true)]!!
                addCode("val %N = ", parameterName).check400 {
                    addStatement("%M(%N, %S)?.let(%N::read)", stringExtractor, requestName, name, readerParameterName)
                }
            } else {
                val stringExtractor = ExtractorFunctions.header[STRING]!!
                addCode("val %N = ", parameterName).check400 {
                    addStatement("%N.read(%M(%N, %S))", readerParameterName, stringExtractor, requestName, name)
                }
            }
        }
    }

    private fun FunSpec.Builder.parseCookieParameter(parameter: KSValueParameter, annotation: KSAnnotation, requestName: String) {
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
            addStatement("val %N = %M(%N, %S)", parameterName, supportedTypeExtractor, requestName, name)
            return
        }
        if (parameterTypeName.isNullable) {
            val stringExtractor = ExtractorFunctions.cookie[STRING.copy(true)]!!
            val readerParameterName = "_${parameterName}StringParameterReader"
            addCode("val %N = ", parameterName).check400 {
                addStatement("%M(%N, %S)?.let(%N::read)", stringExtractor, requestName, name, readerParameterName)
            }
        } else {
            val stringExtractor = ExtractorFunctions.cookie[STRING]!!
            val readerParameterName = "_${parameterName}StringParameterReader"
            addCode("val %N = ", parameterName).check400 {
                addStatement("%N.read(%M(%N, %S))", readerParameterName, stringExtractor, requestName, name)
            }
        }
    }

    private fun FunSpec.Builder.parseQueryParameter(parameter: KSValueParameter, annotation: KSAnnotation, requestName: String) {
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
            addStatement("val %N = %M(%N, %S)", parameterName, supportedTypeExtractor, requestName, name)
            return
        }
        if (parameter.type.resolve().isList()) {
            val readerParameterName = "_${parameterName}StringParameterReader"
            if (parameterTypeName.isNullable) {
                val extractor = MemberName("io.koraframework.http.server.common.request.HttpRequestHandlerUtils", "parseQuerySomeListNullable")
                addCode("val %N = ", parameterName).check400 {
                    addStatement("%M(%N, %S, %N)", extractor, requestName, name, readerParameterName)
                }
            } else {
                val extractor = MemberName("io.koraframework.http.server.common.request.HttpRequestHandlerUtils", "parseQuerySomeList")
                addCode("val %N = ", parameterName).check400 {
                    addStatement("%M(%N, %S, %N)", extractor, requestName, name, readerParameterName)
                }
            }
        } else if (parameter.type.resolve().isSet()) {
            val readerParameterName = "_${parameterName}StringParameterReader"
            if (parameterTypeName.isNullable) {
                val extractor = MemberName("io.koraframework.http.server.common.request.HttpRequestHandlerUtils", "parseQuerySomeSetNullable")
                addCode("val %N = ", parameterName).check400 {
                    addStatement("%M(%N, %S, %N)", extractor, requestName, name, readerParameterName)
                }
            } else {
                val extractor = MemberName("io.koraframework.http.server.common.request.HttpRequestHandlerUtils", "parseQuerySomeSet")
                addCode("val %N = ", parameterName).check400 {
                    addStatement("%M(%N, %S, %N)", extractor, requestName, name, readerParameterName)
                }
            }
        } else {
            if (parameterTypeName.isNullable) {
                val stringExtractor = ExtractorFunctions.query[STRING.copy(true)]!!
                val readerParameterName = "_${parameterName}StringParameterReader"
                addCode("val %N = ", parameterName).check400 {
                    addStatement("%M(%N, %S)?.let(%N::read)", stringExtractor, requestName, name, readerParameterName)
                }
            } else {
                val stringExtractor = ExtractorFunctions.query[STRING]!!
                val readerParameterName = "_${parameterName}StringParameterReader"
                addCode("val %N = ", parameterName).check400 {
                    addStatement("%N.read(%M(%N, %S))", readerParameterName, stringExtractor, requestName, name)
                }
            }
        }
    }

    private fun FunSpec.Builder.check400(callback: (CodeBlock.Builder) -> Unit) {
        controlFlow("try") {
            val b = CodeBlock.builder()
            callback(b)
            addCode(b.build())
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
    private fun FunSpec.Builder.addRequestParameterMapper(parameter: KSValueParameter) {
        val paramName = parameter.name!!.asString()
        val paramType = parameter.type.toTypeName()
        val mapperName = "_${paramName}Mapper"
        val mapping = parameter.parseMappingData().getMapping(httpServerRequestMapper)
        val mappingMapper = mapping?.mapper
        val mapperType = mappingMapper?.toTypeName() ?: httpServerRequestMapper.parameterizedBy(paramType.copy(false))
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
        val interceptorTag = findValueNoDefault<KSType>("tag")
            ?.declaration
            ?.let { it as KSClassDeclaration }
            ?.toClassName()
            ?.toTagAnnotation()
        return Interceptor(interceptorType, interceptorTag)
    }


    private fun Route.funName(function: KSFunctionDeclaration, generatedNames: MutableSet<String>): String {
        val suffix = if (pathTemplate.endsWith("/")) "_trailing_slash" else ""
        val name = method.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_") + pathTemplate.split(Regex("[^A-Za-z0-9]+"))
            .filter { it.isNotBlank() }
            .joinToString("_", "_", suffix)
        // distinct routes may map to the same name, e.g. /files and /files/*
        var uniqueName = name
        var i = 1
        while (!generatedNames.add(uniqueName)) {
            uniqueName = name + "_" + function.simpleName.asString() + (if (i == 1) "" else i.toString())
            i++
        }
        return uniqueName
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
        } else if (returnTypeName != UNIT && returnTypeName.copy(nullable = false) != httpServerResponse) {
            addParameter(ParameterSpec.builder("_responseMapper", mapperClassName).build())
        }
    }
}

internal fun KSFunctionDeclaration.overrideChain(): Sequence<KSFunctionDeclaration> = generateSequence(this) { it.findOverridee() as? KSFunctionDeclaration }

internal fun KSFunctionDeclaration.findHttpRoute(): KSAnnotation? = overrideChain().firstNotNullOfOrNull { it.findAnnotation(httpRoute) }

private fun KSValueParameter.findMappingAnnotation(function: KSFunctionDeclaration, annotation: ClassName): KSAnnotation? {
    val index = function.parameters.indexOf(this)
    return function.overrideChain().firstNotNullOfOrNull { it.parameters[index].findAnnotation(annotation) }
}
