package ru.tinkoff.kora.http.client.symbol.processor

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.isOpen
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.httpClient
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.httpClientAnnotation
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.httpClientEncoderException
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.httpClientException
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.httpClientRequestBuilder
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.httpClientRequestMapper
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.httpClientResponseException
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.httpClientResponseMapper
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.httpClientTelemetryFactory
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.httpRoute
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.interceptWithClassName
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.interceptWithContainerClassName
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.responseCodeMapper
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.responseCodeMappers
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.stringParameterConverter
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.unknownHttpClientException
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValue
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault
import ru.tinkoff.kora.ksp.common.CommonAopUtils.extendsKeepAop
import ru.tinkoff.kora.ksp.common.CommonAopUtils.hasAopAnnotations
import ru.tinkoff.kora.ksp.common.CommonAopUtils.overridingKeepAop
import ru.tinkoff.kora.ksp.common.CommonClassNames.isCollection
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.CommonClassNames.await
import ru.tinkoff.kora.ksp.common.CommonClassNames.isFuture
import ru.tinkoff.kora.ksp.common.FunctionUtils.isSuspend
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.writeTagValue
import ru.tinkoff.kora.ksp.common.KspCommonUtils.findRepeatableAnnotation
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.MappingData
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.ksp.common.parseAnnotationValue
import ru.tinkoff.kora.ksp.common.parseMappingData
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException

class ClientClassGenerator(private val resolver: Resolver) {

    fun generate(declaration: KSClassDeclaration): TypeSpec {
        val typeName = declaration.clientName()
        val methods: List<MethodData> = this.parseMethods(declaration)
        val builder = declaration.extendsKeepAop(resolver, typeName)
            .generated(ClientClassGenerator::class)
        if (hasAopAnnotations(resolver, declaration)) {
            builder.addModifiers(KModifier.OPEN)
        }
        builder.primaryConstructor(this.buildConstructor(builder, declaration, methods))

        for (method in methods) {
            builder.addProperty(method.declaration.simpleName.asString() + "Client", httpClient, KModifier.PRIVATE)
            builder.addProperty(method.declaration.simpleName.asString() + "RequestTimeout", Duration::class.asClassName().copy(true), KModifier.PRIVATE)
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
                        if (parameterType.isCollection()) {
                            parameterType = parameterType.arguments[0].type?.resolve() ?: return@forEach
                        }

                        if (requiresConverter(parameterType)) {
                            result[getConverterName(method, parameter.parameter)] = getConverterTypeName(parameterType)
                        }
                    }

                    is Parameter.HeaderParameter -> {
                        var parameterType = parameter.parameter.type.resolve()
                        if (parameterType.isCollection()) {
                            parameterType = parameterType.arguments[0].type?.resolve() ?: return@forEach
                        }

                        if (requiresConverter(parameterType)) {
                            result[getConverterName(method, parameter.parameter)] = getConverterTypeName(parameterType)
                        }
                    }
                }
            }
        }
        return result
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
        return stringParameterConverter.parameterizedBy(type.makeNotNullable().toTypeName())
    }

    private fun buildFunction(methodData: MethodData): FunSpec {
        val method = methodData.declaration
        val m = method.overridingKeepAop(resolver)
        val b = CodeBlock.builder()
        val methodClientName = method.simpleName.asString() + "Client"
        val methodRequestTimeout = method.simpleName.asString() + "RequestTimeout"

        val httpRoute = method.findAnnotation(HttpClientClassNames.httpRoute)!!
        b.addStatement("val _client = %N", methodClientName)
        val isRBMutable = methodData.parameters.any { it is Parameter.BodyParameter }
        b.addStatement(
            "%L _requestBuilder = %T(%S, %N)",
            if (isRBMutable) "var" else "val",
            httpClientRequestBuilder,
            httpRoute.findValueNoDefault<String>("method")!!,
            method.simpleName.asString() + "Url"
        )
        b.addStatement("    .requestTimeout(%N)", methodRequestTimeout)
        methodData.parameters.forEach { parameter ->
            if (parameter is Parameter.PathParameter) {
                val parameterType = parameter.parameter.type.resolve()
                if (!requiresConverter(parameterType)) {
                    b.add("_requestBuilder.templateParam(%S, %N.toString())\n", parameter.pathParameterName, parameter.parameter.name!!.asString())
                } else {
                    b.addStatement(
                        "_requestBuilder.templateParam(%S, %N.convert(%N))",
                        parameter.pathParameterName,
                        getConverterName(methodData, parameter.parameter),
                        parameter.parameter.name!!.asString()
                    )
                }
            }
            if (parameter is Parameter.HeaderParameter) {
                var parameterType = parameter.parameter.type.resolve()
                var literalName = parameter.parameter.name!!.asString()
                val iterable = parameterType.isCollection()
                val nullable = parameterType.isMarkedNullable
                if (nullable) {
                    b.beginControlFlow("if (%N != null) ", literalName)
                }

                if (iterable) {
                    val argType = parameterType.arguments[0].type?.resolve()
                    val iteratorName = literalName + "_iterator"
                    val paramName = "_" + literalName + "_element"
                    b.addStatement("val %N = %N.iterator()", iteratorName, literalName)
                        .beginControlFlow("while (%N.hasNext())", parameter.parameter.name!!.asString() + "_iterator")
                        .addStatement("val %N = %N.next()", paramName, iteratorName)
                    literalName = paramName

                    if (argType != null) {
                        parameterType = argType
                        if (argType.isMarkedNullable) {
                            b.add("if (%L != null) ", literalName)
                        }
                    }
                }

                if (!requiresConverter(parameterType)) {
                    b.addStatement("_requestBuilder.header(%S, %N.toString())", parameter.headerName, literalName)
                } else {
                    b.addStatement("_requestBuilder.header(%S, %N.convert(%N))", parameter.headerName, getConverterName(methodData, parameter.parameter), literalName)
                }
                if (iterable) {
                    b.endControlFlow().add("\n")
                }
                if (nullable) {
                    b.endControlFlow()
                }
            }
            if (parameter is Parameter.QueryParameter) {
                var parameterType = parameter.parameter.type.resolve()
                var literalName = parameter.parameter.name!!.asString()
                val iterable = parameterType.isCollection()
                val nullable = parameterType.isMarkedNullable

                if (nullable) {
                    b.beginControlFlow("if (%N != null)", literalName)
                }

                if (iterable) {
                    val argType = parameterType.arguments[0].type?.resolve()
                    val iteratorName = literalName + "_iterator"
                    val paramName = "_" + literalName + "_element"
                    b.addStatement("val %N = %N.iterator()", iteratorName, literalName)
                        .beginControlFlow("if (!%N.hasNext())", iteratorName)
                        .addStatement("_requestBuilder.queryParam(%S)", parameter.queryParameterName)
                        .nextControlFlow("else")
                        .add("do {").add(CodeBlock.builder().indent().add("\n").build())
                        .addStatement("val %N = %N.next()", paramName, iteratorName)
                    literalName = paramName

                    if (argType != null) {
                        parameterType = argType
                        if (argType.isMarkedNullable) {
                            b.add("if (%L != null) ", literalName)
                        }
                    }
                }

                if (!requiresConverter(parameterType)) {
                    b.add("_requestBuilder.queryParam(%S, %N.toString())\n", parameter.queryParameterName, literalName)
                } else {
                    b.add(
                        "  _requestBuilder.queryParam(%S, %L.convert(%L))\n",
                        parameter.queryParameterName,
                        getConverterName(methodData, parameter.parameter),
                        literalName
                    )
                }

                if (iterable) {
                    b.add(CodeBlock.builder().unindent().build()).add("\n")
                        .add("} while (%N.hasNext())", parameter.parameter.name!!.asString() + "_iterator")
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
                b.controlFlow("try") {
                    b.add("_requestBuilder = %L.apply(%T.current(), _requestBuilder, %N)\n", requestMapperName, CommonClassNames.context, parameter.parameter.name!!.asString())
                    nextControlFlow("catch (_e: %T)", RuntimeException::class.asClassName())
                    b.add("throw _e\n")
                    nextControlFlow("catch (_e: Exception)")
                    b.add("throw %T(_e)\n", httpClientEncoderException)
                }
            }
        }

        b.addStatement("val _request = _requestBuilder.build()")
        if (method.isSuspend()) {
            b.add("_client.execute(_request).%M().%M { _response ->", await, MemberName("kotlin.io", "use")).indent().add("\n")
        } else {
            b.add("try {").indent().add("\n")
            b.add("_client.execute(_request).toCompletableFuture().get().%M { _response ->", MemberName("kotlin.io", "use")).indent().add("\n")
        }
        if (methodData.responseMapper?.mapper != null) {
            val responseMapperName = method.simpleName.asString() + "ResponseMapper"
            b.add("return %N.apply(_response)", responseMapperName)
            if (method.isSuspend()) b.add(".%M()", await)
        } else if (methodData.codeMappers.isEmpty()) {
            b.addStatement("val _code = _response.code()")
            b.controlFlow("if (_code in 200..299)") {
                if (methodData.returnType.declaration.qualifiedName?.asString() == "kotlin.Unit") {
                    b.add("return\n")
                } else {
                    val responseMapperName = method.simpleName.asString() + "ResponseMapper"
                    addStatement("return %N.apply(_response)", responseMapperName)
                    if (method.isSuspend()) b.add(".%M()", await)
                }
                nextControlFlow("else")
                add("throw %T.fromResponseFuture(_response)", httpClientResponseException)
                if (method.isSuspend()) {
                    add(".%M()\n", await)
                } else {
                    add(".get()\n")
                }
            }
        } else {
            b.add("val _code = _response.code()\n")
            b.controlFlow("return when (_code)") {
                var defaultMapper: ResponseCodeMapperData? = null
                methodData.codeMappers.forEach { codeMapper ->
                    if (codeMapper.code == -1) {
                        defaultMapper = codeMapper
                    } else {
                        val responseMapperName = method.simpleName.asString() + codeMapper.code.toString() + "ResponseMapper"
                        if (codeMapper.assignable) {
                            add("%L -> %L.apply(_response)", codeMapper.code, responseMapperName)
                        } else {
                            add("%L -> throw %L.apply(_response)", codeMapper.code, responseMapperName)
                        }
                        if (method.isSuspend()) b.add(".%M()", await)
                        b.add("\n")
                    }
                }
                if (defaultMapper == null) {
                    add("else -> throw %T.fromResponseFuture(_response)", httpClientResponseException)
                    if (method.isSuspend()) {
                        add(".%M()\n", await)
                    } else {
                        add(".get()\n")
                    }
                } else {
                    if (defaultMapper!!.assignable) {
                        add("else -> %L.apply(_response)", method.simpleName.asString() + "DefaultResponseMapper")
                    } else {
                        add("else -> throw %L.apply(_response)", method.simpleName.asString() + "DefaultResponseMapper")
                    }
                    if (method.isSuspend()) b.add(".%M()", await)
                    b.add("\n")
                }
            }
        }

        if (method.isSuspend()) {
            b.unindent().add("\n}\n")
        } else {
            b.unindent()
            b.add("\n}")// use
            b.unindent()
            b.add("\n} catch (_e: %T) {\n", ExecutionException::class.asClassName())
            b.add("  _e.cause?.let {\n")
            b.add("    if (it is %T) throw it\n", RuntimeException::class.asClassName())
            b.add("    throw %T(it)\n", unknownHttpClientException)
            b.add("  }\n")
            b.add("  throw %T(_e)\n", unknownHttpClientException)
            b.add("} catch (_e: %T) {\n", RuntimeException::class.asClassName())
            b.add("  throw _e\n")
            b.add("} catch (_e: Exception) {\n")
            b.add("  throw %T(_e)\n", unknownHttpClientException)
            b.add("}\n")
        }
        return m.addCode(b.build()).build()
    }


    private fun buildConstructor(tb: TypeSpec.Builder, declaration: KSClassDeclaration, methods: List<MethodData>): FunSpec {
        val parameterConverters = parseParametersConverters(methods)
        val packageName = declaration.packageName.asString()
        val configClassName = declaration.configName()
        val annotation = declaration.findAnnotation(httpClientAnnotation)!!
        val telemetryTag = annotation.findValue<List<KSType>>("telemetryTag")
        val httpClientTag = annotation.findValue<List<KSType>>("httpClientTag")
        val clientParameter = ParameterSpec.builder("httpClient", httpClient)
        if (!httpClientTag.isNullOrEmpty()) {
            clientParameter.addAnnotation(AnnotationSpec.builder(CommonClassNames.tag).addMember("value = %L", httpClientTag.writeTagValue()).build())
        }
        val telemetryParameter = ParameterSpec.builder("telemetryFactory", httpClientTelemetryFactory)
        if (!telemetryTag.isNullOrEmpty()) {
            telemetryParameter.addAnnotation(AnnotationSpec.builder(CommonClassNames.tag).addMember("value = %L", telemetryTag.writeTagValue()).build())
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

            methodData.parameters.asSequence().filterIsInstance<Parameter.BodyParameter>().forEach { parameter ->
                val typeParameterResolver = parameter.parameter.type.resolve().declaration.typeParameters.toTypeParameterResolver()
                val requestMapperType = parameter.mapper?.mapper?.toTypeName(typeParameterResolver)
                    ?: httpClientRequestMapper.parameterizedBy(parameter.parameter.type.toTypeName(typeParameterResolver))

                val paramName = method.simpleName.asString() + "RequestMapper"
                val tags = parameter.mapper?.toTagAnnotation()
                val constructorParameter = ParameterSpec.builder(paramName, requestMapperType)
                if (tags != null) {
                    constructorParameter.addAnnotation(tags)
                }
                tb.addProperty(PropertySpec.builder(paramName, requestMapperType, KModifier.PRIVATE).initializer(paramName).build())
                builder.addParameter(constructorParameter.build())
            }
            if (methodData.responseMapper?.mapper != null) {
                val responseMapperName = method.simpleName.asString() + "ResponseMapper"
                val responseMapperTypeName = methodData.responseMapper.mapper!!.toTypeName()
                addResponseMapper(responseMapperName, methodData.responseMapper.mapper, responseMapperTypeName, methodData, tb, builder)
            } else if (methodData.codeMappers.isEmpty()) {
                if (methodData.returnType.declaration.qualifiedName?.asString() != "kotlin.Unit") {
                    val responseMapperName = method.simpleName.asString() + "ResponseMapper"
                    val responseMapperTypeName = methodData.responseMapperType()
                    addResponseMapper(responseMapperName, methodData.responseMapper?.mapper, responseMapperTypeName, methodData, tb, builder)
                }
            } else {
                for (codeMapper in methodData.codeMappers) {
                    val responseMapperName = method.simpleName.asString() + (if (codeMapper.code > 0) codeMapper.code else "Default").toString() + "ResponseMapper"
                    val responseMapperType = codeMapper.responseMapperType(method.returnType!!.resolve(), method.isSuspend())
                    addResponseMapper(responseMapperName, codeMapper.mapper, responseMapperType, methodData, tb, builder)
                }
            }
            val name = method.simpleName.asString()
            builder.addCode(
                "val %L = config.apply(httpClient, %T::class.java, %S, config.%LConfig, telemetryFactory, %S)\n",
                name,
                declaration.toClassName(),
                name,
                name,
                method.findAnnotation(httpRoute)!!.findValueNoDefault<String>("path")!!
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

    private fun addResponseMapper(
        mapperName: String,
        mapperType: KSType?,
        mapperTypeName: TypeName,
        methodData: MethodData,
        tb: TypeSpec.Builder,
        builder: FunSpec.Builder
    ) {
        val declaration = mapperType?.declaration
        if (declaration is KSClassDeclaration && !declaration.isOpen() && declaration.getConstructors().count() == 1 && declaration.getConstructors().first().parameters.isEmpty()) {
            tb.addProperty(PropertySpec.builder(mapperName, mapperTypeName, KModifier.PRIVATE).initializer("%T()", declaration.toClassName()).build())
            return
        }
        val mapperParameter = ParameterSpec.builder(mapperName, mapperTypeName)
        val responseMapperTags = methodData.responseMapper?.toTagAnnotation()
        if (responseMapperTags != null) {
            mapperParameter.addAnnotation(responseMapperTags)
        }
        tb.addProperty(PropertySpec.builder(mapperName, mapperTypeName, KModifier.PRIVATE).initializer("%L", mapperName).build())
        builder.addParameter(mapperParameter.build())
    }

    private fun parseMethods(declaration: KSClassDeclaration): List<MethodData> {
        val result = ArrayList<MethodData>()
        declaration.getDeclaredFunctions().forEach { function ->
            val parameters = mutableListOf<Parameter>()
            for (i in function.parameters.indices) {
                val parameter = Parameter.parseParameter(function, i)
                parameters.add(parameter)
            }
            val returnType = function.returnType!!.resolve()
            val responseCodeMappers = this.parseMapperData(function)
            val responseMapper = function.parseMappingData().getMapping(httpClientResponseMapper)
            result.add(MethodData(function, returnType, responseMapper, responseCodeMappers, parameters))
        }
        return result
    }

    private fun parseMapperData(declaration: KSFunctionDeclaration): List<ResponseCodeMapperData> {
        return declaration.findRepeatableAnnotation(responseCodeMapper, responseCodeMappers).map { mapper ->
            val type = mapper.findValueNoDefault<KSType>("type")
            val mapperType = mapper.findValueNoDefault<KSType>("mapper")
            if (type == null && mapperType == null) {
                throw ProcessingErrorException("Either 'type' or 'mapper' should be specified", declaration)
            }
            val isAssignable = when {
                type != null -> declaration.returnType!!.resolve().isAssignableFrom(type)
                mapperType != null -> {
                    val supertype = mapperType.findSupertype(httpClientResponseMapper)!!
                    var typeArg = supertype.arguments[0].type!!.resolve()
                    if (typeArg.isFuture()) {
                        typeArg = typeArg.arguments[0].type!!.resolve()
                    }
                    typeArg.declaration is KSTypeParameter || declaration.returnType!!.resolve().isAssignableFrom(typeArg)
                }
                else -> throw IllegalStateException()
            }
            val code = mapper.findValueNoDefault<Int>("code")!!

            ResponseCodeMapperData(code, type, mapperType, isAssignable)
        }
    }

    data class MethodData(
        val declaration: KSFunctionDeclaration,
        val returnType: KSType,
        val responseMapper: MappingData?,
        val codeMappers: List<ResponseCodeMapperData>,
        val parameters: List<Parameter>
    ) {
        fun responseMapperType() = if (declaration.isSuspend()) {
            httpClientResponseMapper.parameterizedBy(CompletionStage::class.asClassName().parameterizedBy(
                returnType.toTypeName()
            ))
        } else {
            httpClientResponseMapper.parameterizedBy(returnType.toTypeName())
        }
    }

    data class ResponseCodeMapperData(val code: Int, val type: KSType?, val mapper: KSType?, val assignable: Boolean) {
        fun responseMapperType(returnType: KSType, suspend: Boolean): TypeName {
            if (type != null) {
                val typeName = type.toTypeName().copy(nullable = false)
                return httpClientResponseMapper.parameterizedBy(if (suspend) CompletionStage::class.asClassName().parameterizedBy(typeName) else typeName)
            } else if (mapper != null) {
                if (mapper.declaration is KSClassDeclaration && mapper.declaration.typeParameters.isNotEmpty()) {
                    val typeArg = returnType.toTypeName().copy(false)
                    return mapper.toClassName().parameterizedBy(mapper.declaration.typeParameters.map { typeArg})
                }
                return mapper.toTypeName()
            }
            throw IllegalStateException()
        }
    }

    data class Interceptor(val type: TypeName, val tag: AnnotationSpec?)

    private fun parseInterceptor(it: KSAnnotation): Interceptor {
        val interceptorType = parseAnnotationValue<KSType>(it, "value")!!.toTypeName()
        val interceptorTag = parseAnnotationValue<KSAnnotation>(it, "tag")
        val interceptorTagAnnotationSpec = if (interceptorTag == null) {
            null
        } else {
            val tag = AnnotationSpec.builder(CommonClassNames.tag)
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

private fun KSType.findSupertype(httpClientResponseMapper: ClassName): KSType? {
    val decl = this.declaration
    if (decl !is KSClassDeclaration) {
        return null
    }
    for (superType in decl.superTypes) {
        val supertypeResolved = superType.resolve()
        val supertypeDecl = supertypeResolved.declaration
        if (supertypeDecl is KSClassDeclaration && supertypeDecl.qualifiedName?.asString() == httpClientResponseMapper.canonicalName) {
            return supertypeResolved
        }
    }
    return null
}
