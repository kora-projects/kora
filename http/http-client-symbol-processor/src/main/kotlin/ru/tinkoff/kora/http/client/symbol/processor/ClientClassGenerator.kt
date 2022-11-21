package ru.tinkoff.kora.http.client.symbol.processor

import com.google.devtools.ksp.*
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.jvm.throws
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.http.client.common.HttpClient
import ru.tinkoff.kora.http.client.common.HttpClientException
import ru.tinkoff.kora.http.client.common.HttpClientResponseException
import ru.tinkoff.kora.http.client.common.annotation.ResponseCodeMapper
import ru.tinkoff.kora.http.client.common.annotation.ResponseCodeMapper.ResponseCodeMappers
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestBuilder
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestMapper
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetryFactory
import ru.tinkoff.kora.http.client.common.writer.StringParameterConverter
import ru.tinkoff.kora.http.common.annotation.HttpRoute
import ru.tinkoff.kora.kora.app.ksp.extendsKeepAop
import ru.tinkoff.kora.kora.app.ksp.hasAopAnnotations
import ru.tinkoff.kora.kora.app.ksp.overridingKeepAop
import ru.tinkoff.kora.ksp.common.*
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValue
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.writeTagValue
import ru.tinkoff.kora.ksp.common.KspCommonUtils.findRepeatableAnnotation
import java.util.*
import javax.annotation.processing.Generated

@KspExperimental
class ClientClassGenerator(private val resolver: Resolver) {
    private val defaultMapperType = resolver.getClassDeclarationByName(ResponseCodeMapper.DefaultHttpClientResponseMapper::class.qualifiedName!!)?.asStarProjectedType()
    private val awaitSingle = MemberName("kotlinx.coroutines.reactor", "awaitSingle")
    private val awaitSingleOrNull = MemberName("kotlinx.coroutines.reactor", "awaitSingleOrNull")
    private val parameterParser = Parameter.ParameterParser(resolver)
    private val returnTypeParser = ReturnType.ReturnTypeParser(resolver)
    private val httpResponseType = resolver.getClassDeclarationByName(HttpClientResponse::class.qualifiedName!!)?.asStarProjectedType()
    private val requestMapperDeclaration = resolver.getClassDeclarationByName(HttpClientRequestMapper::class.qualifiedName!!)
    private val interceptWithClassName = ClassName("ru.tinkoff.kora.http.common.annotation", "InterceptWith")
    private val interceptWithContainerClassName = ClassName("ru.tinkoff.kora.http.common.annotation", "InterceptWith", "InterceptWithContainer")

    private val listType = resolver.getClassDeclarationByName(List::class.qualifiedName!!)!!.asStarProjectedType()

    fun generate(declaration: KSClassDeclaration): TypeSpec {
        val typeName = declaration.clientName()
        val methods: List<MethodData> = this.parseMethods(declaration)
        val builder = extendsKeepAop(declaration, resolver, typeName)
            .addAnnotation(AnnotationSpec.builder(Generated::class).addMember("%S", ClientClassGenerator::class.java.canonicalName).build())
        if (hasAopAnnotations(resolver, declaration)) {
            builder.addModifiers(KModifier.OPEN)
        }
        builder.primaryConstructor(this.buildConstructor(builder, declaration, methods))

        for (method in methods) {
            builder.addProperty(method.declaration.simpleName.asString() + "Client", HttpClient::class, KModifier.PRIVATE)
            builder.addProperty(method.declaration.simpleName.asString() + "RequestTimeout", Int::class, KModifier.PRIVATE)
            builder.addProperty(method.declaration.simpleName.asString() + "Url", String::class, KModifier.PRIVATE)
            val funSpec: FunSpec = this.buildFunction(method)
            builder.addFunction(funSpec)
        }
        return builder.build()
    }

    private fun parseParametersConverters(methods: List<MethodData>): Map<String, ParameterizedTypeName> {
        val result = hashMapOf<String, ParameterizedTypeName>()
        methods.forEach { method ->
            method.parameters.forEach { parameter ->
                when (parameter) {
                    is Parameter.PathParameter -> {
                        val parameterType = parameter.parameter.type.resolve()
                        if (requiresConverter(parameterType)) {
                            result[getConverterName(method, parameter.parameter)] = getConverterTypeName(parameterType)
                        }
                    }
                    is Parameter.QueryParameter -> {
                        var parameterType = parameter.parameter.type.resolve()
                        if (isIterable(parameterType)) {
                            parameterType = parameterType.arguments[0].type?.resolve() ?: return@forEach
                        }

                        if (requiresConverter(parameterType)) {
                            result[getConverterName(method, parameter.parameter)] = getConverterTypeName(parameterType)
                        }
                    }

                    is Parameter.HeaderParameter -> {
                        val parameterType = parameter.parameter.type.resolve()
                        if (requiresConverter(parameterType)) {
                            result[getConverterName(method, parameter.parameter)] = getConverterTypeName(parameterType)
                        }
                    }
                }
            }
        }
        return result
    }

    private fun isIterable(type: KSType): Boolean {
        val notNullType = type.makeNotNullable().starProjection()
        return notNullType == listType ||
            notNullType == resolver.builtIns.iterableType
    }

    private fun requiresConverter(type: KSType): Boolean {
        val notNullType = type.makeNotNullable()
        return notNullType != resolver.builtIns.stringType &&
            notNullType != resolver.builtIns.numberType &&
            notNullType != resolver.builtIns.byteType &&
            notNullType != resolver.builtIns.shortType &&
            notNullType != resolver.builtIns.intType &&
            notNullType != resolver.builtIns.longType &&
            notNullType != resolver.builtIns.floatType &&
            notNullType != resolver.builtIns.doubleType &&
            notNullType != resolver.builtIns.charType &&
            notNullType != resolver.builtIns.booleanType
    }

    private fun getConverterName(methodData: MethodData, parameter: KSValueParameter): String {
        return methodData.declaration.simpleName.asString() + parameter.name!!.asString().replaceFirstChar { it.uppercaseChar() } + "Converter"
    }

    private fun getConverterTypeName(type: KSType): ParameterizedTypeName {
        return StringParameterConverter::class.asClassName().parameterizedBy(type.makeNotNullable().toTypeName())
    }

    private fun buildFunction(methodData: MethodData): FunSpec {
        val method = methodData.declaration
        val b = overridingKeepAop(method, resolver)
            .throws(HttpClientException::class)
        val methodClientName: String = method.simpleName.asString() + "Client"
        val methodRequestTimeout: String = method.simpleName.asString() + "RequestTimeout"
        val httpRoute = method.getAnnotationsByType(HttpRoute::class).first()
        b.addStatement("val _client = %L", methodClientName)
        val isRBMutable = methodData.parameters.any { it is Parameter.BodyParameter }
        b.addStatement(
            "%L _requestBuilder = %T(%S, %LUrl)",
            if (isRBMutable) "var" else "val",
            HttpClientRequestBuilder::class,
            httpRoute.method,
            method.simpleName.asString()
        )
        b.addStatement("    .requestTimeout(%L)", methodRequestTimeout)
        methodData.parameters.forEach { parameter ->
            if (parameter is Parameter.PathParameter) {
                val parameterType = parameter.parameter.type.resolve()
                if (!requiresConverter(parameterType)) {
                    b.addCode("_requestBuilder.templateParam(%S, %T.toString(%L))\n", parameter.pathParameterName, Objects::class, parameter.parameter.name!!.asString())
                } else {
                    b.addStatement(
                        "_requestBuilder.templateParam(%S, %L.convert(%L))",
                        parameter.pathParameterName,
                        getConverterName(methodData, parameter.parameter),
                        parameter.parameter.name!!.asString()
                    )
                }
            }
            if (parameter is Parameter.HeaderParameter) {
                val parameterType = parameter.parameter.type.resolve()
                if (parameterType.isMarkedNullable) {
                    b.addCode("if (%L != null) ", parameter.parameter.name!!.asString())
                }
                if (!requiresConverter(parameterType)) {
                    b.addStatement("_requestBuilder.header(%S, %T.toString(%L))", parameter.headerName, Objects::class, parameter.parameter.name!!.asString())
                } else {
                    b.addStatement("_requestBuilder.header(%S, %L.convert(%L))", parameter.headerName, getConverterName(methodData, parameter.parameter), parameter.parameter.name!!.asString())
                }
            }
            if (parameter is Parameter.QueryParameter) {
                var parameterType = parameter.parameter.type.resolve()
                var literalName = parameter.parameter.name!!.asString()
                val iterable = isIterable(parameterType)
                val nullable = parameterType.isMarkedNullable

                if (nullable) {
                    b.beginControlFlow("if (%L != null)", literalName)
                }


                if (iterable) {
                    val argType = parameterType.arguments[0].type?.resolve()

                    val paramName = "_" + literalName + "_element"
                    b.beginControlFlow("for (%L in %L)", paramName, literalName)
                    literalName = paramName

                    if (argType != null) {
                        parameterType = argType
                        if (argType.isMarkedNullable) {
                            b.addCode("if (%L != null) ", literalName)
                        }
                    }
                }

                if (!requiresConverter(parameterType)) {
                    b.addCode("_requestBuilder.queryParam(%S, %T.toString(%L))\n", parameter.queryParameterName, Objects::class, literalName)
                } else {
                    b.addCode(
                        "  _requestBuilder.queryParam(%S, %L.convert(%L))\n",
                        parameter.queryParameterName,
                        getConverterName(methodData, parameter.parameter),
                        literalName
                    )
                }

                if (iterable) {
                    b.endControlFlow()
                }
                if (nullable) {
                    b.endControlFlow()
                }
            }
        }
        methodData.parameters.forEach { parameter ->
            if (parameter is Parameter.BodyParameter) {
                val requestMapperName: String = method.simpleName.asString() + "RequestMapper"
                b.addCode("_requestBuilder = %L.apply(\n", requestMapperName)
                b.addCode(" %T(_requestBuilder, %L)\n", HttpClientRequestMapper.Request::class, parameter.parameter.name!!.asString())
                b.addCode(")\n")
            }
        }

        b.addStatement("val _request = _requestBuilder.build()")
        val publisherType = if (methodData.returnType is ReturnType.FluxReturnType) Flux::class.java else Mono::class.java
        val responseProcess = CodeBlock.builder()
        if (methodData.responseMapper != null || httpResponseType == methodData.returnType.publisherParameter()) {
            val responseMapperName: String = method.simpleName.asString() + "ResponseMapper"
            responseProcess.addStatement("%L.apply(_response)", responseMapperName)
        } else if (methodData.codeMappers.isEmpty()) {
            val responseMapperName: String = method.simpleName.asString() + "ResponseMapper"
            responseProcess.addStatement("val _code = _response.code()")
            responseProcess.controlFlow("if (_code in 200..299)") {
                addStatement("%L.apply(_response)", responseMapperName)
                nextControlFlow("else")
                addStatement("%T.fromResponse(_response)", HttpClientResponseException::class)
            }
        } else {
            responseProcess.add("val _code = _response.code()\n")
            responseProcess.controlFlow("when (_code)") {
                var defaultMapper: ResponseCodeMapperData? = null
                methodData.codeMappers.forEach { codeMapper ->
                    if (codeMapper.code == ResponseCodeMapper.DEFAULT) {
                        defaultMapper = codeMapper
                    } else {
                        val responseMapperName = method.simpleName.asString() + codeMapper.code.toString() + "ResponseMapper"
                        addStatement("%L -> %L.apply(_response)\n", codeMapper.code, responseMapperName)
                    }
                }
                if (defaultMapper == null) {
                    addStatement("else -> %T.fromResponse(_response)\n", HttpClientResponseException::class.java)
                } else {
                    addStatement("else -> %L.apply(_response)\n", method.simpleName.asString() + "DefaultResponseMapper")
                }
            }
        }
        b.controlFlow("val _resourceClosure = { _response:%T -> ", HttpClientResponse::class) {
            addCode("%L", responseProcess.build())
        }
        b.addStatement(
            "val _result = %T.usingWhen(\n   _client.execute(_request), \n   _resourceClosure, \n    %T::close\n)",
            publisherType,
            HttpClientResponse::class
        )

        if (methodData.returnType is ReturnType.FluxReturnType || methodData.returnType is ReturnType.MonoReturnType) {
            b.addCode("return _result\n")
        } else if (methodData.returnType is ReturnType.SimpleReturnType) {
            if (methodData.returnType.ksType.isMarkedNullable) {
                b.addCode("return _result.block()\n")
            } else {
                b.addCode("return _result.block()!!\n")
            }
        } else if (methodData.returnType is ReturnType.SuspendReturnType) {
            if (methodData.returnType.returnType.isMarkedNullable) {
                b.addCode("return _result.%M()\n", awaitSingleOrNull)
            } else {
                b.addCode("return _result.%M()\n", awaitSingle)
            }
        } else {
            b.addCode("_result.block()\n")
        }
        return b.build()
    }


    private fun buildConstructor(tb: TypeSpec.Builder, declaration: KSClassDeclaration, methods: List<MethodData>): FunSpec {
        val parameterConverters = parseParametersConverters(methods)
        val packageName = declaration.packageName.asString()
        val configClassName = declaration.configName()
        val annotation = declaration.findAnnotation(ru.tinkoff.kora.http.client.common.annotation.HttpClient::class)!!


        val telemetryTag = annotation.findValue<List<KSType>>("telemetryTag")
        val httpClientTag = annotation.findValue<List<KSType>>("httpClientTag")
        val clientParameter = ParameterSpec.builder("httpClient", HttpClient::class.asClassName())
        if (!httpClientTag.isNullOrEmpty()) {
            clientParameter.addAnnotation(AnnotationSpec.builder(Tag::class).addMember("value = %L", httpClientTag.writeTagValue()).build())
        }
        val telemetryParameter = ParameterSpec.builder("telemetryFactory", HttpClientTelemetryFactory::class.asTypeName())
        if (!telemetryTag.isNullOrEmpty()) {
            telemetryParameter.addAnnotation(AnnotationSpec.builder(Tag::class).addMember("value = %L", telemetryTag.writeTagValue()).build())
        }
        val classInterceptors = declaration.findRepeatableAnnotation(interceptWithClassName, interceptWithContainerClassName)
            .map { parseInterceptor(it) }
        var interceptorsCount = 0
        val addedInterceptorsMap = HashMap<Interceptor, String>()
        val builder = FunSpec.constructorBuilder()
            .addParameter(clientParameter.build())
            .addParameter("config", ClassName(packageName, configClassName))
            .addParameter(telemetryParameter.build())
        parameterConverters.forEach { (converterName, converterType) ->
            tb.addProperty(PropertySpec.builder(converterName, converterType, KModifier.PRIVATE).initializer(converterName).build())
            builder.addParameter(converterName, converterType)
        }
        for (classInterceptor in classInterceptors) {
            if (addedInterceptorsMap.containsKey(classInterceptor)) {
                continue
            }
            val name = "_interceptor${interceptorsCount + 1}"
            val p = ParameterSpec.builder(name, classInterceptor.type)
            if (classInterceptor.tag != null) {
                p.addAnnotation(classInterceptor.tag)
            }
            val parameter = p.build()
            builder.addParameter(parameter)
            addedInterceptorsMap[classInterceptor] = name
            interceptorsCount++
        }
        methods.forEach { methodData: MethodData ->
            val method = methodData.declaration
            val methodInterceptors = methodData.declaration.findRepeatableAnnotation(interceptWithClassName, interceptWithContainerClassName)
                .map { parseInterceptor(it) }
                .filter { !classInterceptors.contains(it) }
                .toCollection(LinkedHashSet())
                .toList()

            methodData.parameters.forEach { parameter ->
                if (parameter is Parameter.BodyParameter) {
                    val typeParameterResolver = parameter.parameter.type.resolve().declaration.typeParameters.toTypeParameterResolver()
                    val requestMapperType = parameter.mapper?.mapper?.toTypeName(typeParameterResolver) ?: requestMapperDeclaration!!.toClassName().parameterizedBy(
                        parameter.parameter.type.toTypeName(typeParameterResolver)
                    )
                    val paramName = method.simpleName.asString() + "RequestMapper"
                    val tags = parameter.mapper?.toTagAnnotation()
                    val constructorParameter = ParameterSpec.builder(paramName, requestMapperType)
                    if (tags != null) {
                        constructorParameter.addAnnotation(tags)
                    }
                    tb.addProperty(PropertySpec.builder(paramName, requestMapperType, KModifier.PRIVATE).initializer(paramName).build())
                    builder.addParameter(constructorParameter.build())
                }
            }
            if (methodData.codeMappers.isEmpty()) {
                val responseMapperName: String = method.simpleName.asString() + "ResponseMapper"
                val responseMapperType = if (methodData.responseMapper?.mapper != null) methodData.responseMapper.mapper!!.toTypeName() else methodData.returnType.responseMapperType()
                addParameterMapper(responseMapperName, responseMapperType, methodData, tb, builder)
            } else {
                for (codeMapper in methodData.codeMappers) {
                    val responseMapperName = method.simpleName.asString() + (if (codeMapper.code > 0) codeMapper.code else "Default").toString() + "ResponseMapper"
                    val responseMapperType = codeMapper.responseMapperType(methodData.returnType.publisherType(), defaultMapperType!!)
                    addParameterMapper(responseMapperName, responseMapperType, methodData, tb, builder)
                }
            }
            val name = method.simpleName.asString()
            builder.addCode(
                "val %L = config.apply(httpClient, %T::class.java, %S, config.%LConfig, telemetryFactory, %S)\n",
                name,
                declaration.toClassName(),
                name,
                name,
                method.getAnnotationsByType(HttpRoute::class).first().path
            )
            builder.addCode("this.%LUrl = %L.url\n", name, name)
            builder.addCode("this.%LRequestTimeout = %L.requestTimeout\n", name, name)
            builder.addCode("this.%LClient = %L.client\n", name, name)
            if (methodInterceptors.isNotEmpty() || classInterceptors.isNotEmpty()) {
                builder.addCode("\n")
                for (methodInterceptor in methodInterceptors) {
                    if (addedInterceptorsMap.containsKey(methodInterceptor)) {
                        continue
                    }
                    val interceptorName = "_interceptor" + (interceptorsCount + 1)
                    val p = ParameterSpec.builder(interceptorName, methodInterceptor.type)
                    if (methodInterceptor.tag != null) {
                        p.addAnnotation(methodInterceptor.tag)
                    }
                    val parameter = p.build()
                    builder.addParameter(parameter)
                    addedInterceptorsMap[methodInterceptor] = interceptorName
                    interceptorsCount++
                }
                for (methodInterceptor in methodInterceptors.reversed()) {
                    builder.addCode("  .with(%L)", addedInterceptorsMap[methodInterceptor])
                }
                for (methodInterceptor in classInterceptors.reversed()) {
                    builder.addCode("  .with(%L)", addedInterceptorsMap[methodInterceptor])
                }
                builder.addCode("\n")
            }
        }
        return builder.build()
    }

    private fun addParameterMapper(
        responseMapperName: String,
        responseMapperType: TypeName,
        methodData: MethodData,
        tb: TypeSpec.Builder,
        builder: FunSpec.Builder
    ) {
        val responseMapperParameter = ParameterSpec.builder(responseMapperName, responseMapperType)

        val responseMapperTags = methodData.responseMapper?.toTagAnnotation()
        if (responseMapperTags != null) {
            responseMapperParameter.addAnnotation(responseMapperTags)
        }
        tb.addProperty(PropertySpec.builder(responseMapperName, responseMapperType, KModifier.PRIVATE).initializer("%L", responseMapperName).build())
        builder.addParameter(responseMapperParameter.build())
    }

    private fun parseMethods(declaration: KSClassDeclaration): List<MethodData> {
        val result = ArrayList<MethodData>()
        declaration.getDeclaredFunctions().forEach { function ->
            val parameters = mutableListOf<Parameter>()
            for (i in function.parameters.indices) {
                val parameter = this.parameterParser.parseParameter(function, i)
                parameters.add(parameter)
            }
            val returnType: ReturnType = this.returnTypeParser.parseReturnType(function)
            val responseCodeMappers = this.parseMapperData(function)
            val responseMapper = function.parseMappingData().getMapping(function.returnType!!.resolve())
            result.add(MethodData(function, returnType, responseMapper, responseCodeMappers, parameters))
        }
        return result
    }

    private fun parseMapperData(declaration: KSFunctionDeclaration): List<ResponseCodeMapperData> {
        val mappers = declaration.getAnnotationsByType(ResponseCodeMappers::class).firstOrNull()

        val responseCodeMappers: List<ResponseCodeMapper> = mappers?.value?.toList() ?: declaration.getAnnotationsByType(ResponseCodeMapper::class).toList().map { it }

        return responseCodeMappers.map { mapper ->
            val type = try {
                resolver.getClassDeclarationByName(mapper.type.qualifiedName!!)?.asStarProjectedType()
            } catch (e: KSTypeNotPresentException) {
                e.ksType
            }
            val mapperType = try {
                resolver.getClassDeclarationByName(mapper.mapper.qualifiedName!!)?.asStarProjectedType()
            } catch (e: KSTypeNotPresentException) {
                e.ksType
            }
            ResponseCodeMapperData(mapper.code, type, mapperType)
        }
    }

    data class MethodData(
        val declaration: KSFunctionDeclaration,
        val returnType: ReturnType,
        val responseMapper: MappingData?,
        val codeMappers: List<ResponseCodeMapperData>,
        val parameters: List<Parameter>
    )

    data class ResponseCodeMapperData(val code: Int, val type: KSType?, val mapper: KSType?) {
        fun responseMapperType(publisherType: ClassName, defaultMapperType: KSType): TypeName {
            if (type != null && mapper != null && mapper == defaultMapperType) {
                return HttpClientResponseMapper::class.asClassName().parameterizedBy(type.toTypeName().copy(nullable = false), publisherType.parameterizedBy(type.toTypeName().copy(nullable = false)))
            } else if (mapper != null) {
                return mapper.toTypeName()
            }
            // response passed through mono and mono can't have null values, only empty ones
            return HttpClientResponseMapper::class.asClassName().parameterizedBy(type!!.toTypeName().copy(nullable = false), publisherType.parameterizedBy(type.toTypeName().copy(nullable = false)))
        }
    }

    data class Interceptor(val type: TypeName, val tag: AnnotationSpec?)

    private fun parseInterceptor(it: KSAnnotation): Interceptor {
        val interceptorType = parseAnnotationValue<KSType>(it, "value")!!.toTypeName()
        val interceptorTag = parseAnnotationValue<KSAnnotation>(it, "tag")
        val interceptorTagAnnotationSpec = if (interceptorTag == null) {
            null
        } else {
            val tag = AnnotationSpec.builder(Tag::class)
            val builder = CodeBlock.builder().add("value = [")
            val tags = interceptorTag.arguments[0].value!! as List<KSType>
            if (tags.isNotEmpty()) {
                for (t in tags) {
                    builder.add("%T::class, ", t.toTypeName())
                }
                builder.add("]")
                tag.addMember(builder.build()).build()
            } else {
                null
            }
        }
        return Interceptor(interceptorType, interceptorTagAnnotationSpec)
    }
}
