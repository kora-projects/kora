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
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.httpBody
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.httpClient
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.httpClientAnnotation
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.httpClientEncoderException
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.httpClientException
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.httpClientRequest
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.httpClientRequestMapper
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.httpClientResponseException
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.httpClientResponseMapper
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.httpClientTelemetryFactory
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.httpClientUnknownException
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.httpHeaders
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.httpRoute
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.interceptWithClassName
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.interceptWithContainerClassName
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.responseCodeMapper
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.responseCodeMappers
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.stringParameterConverter
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.uriQueryBuilder
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValue
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault
import ru.tinkoff.kora.ksp.common.CommonAopUtils.extendsKeepAop
import ru.tinkoff.kora.ksp.common.CommonAopUtils.hasAopAnnotations
import ru.tinkoff.kora.ksp.common.CommonAopUtils.overridingKeepAop
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.CommonClassNames.await
import ru.tinkoff.kora.ksp.common.CommonClassNames.isCollection
import ru.tinkoff.kora.ksp.common.CommonClassNames.isCompletionStage
import ru.tinkoff.kora.ksp.common.CommonClassNames.isMap
import ru.tinkoff.kora.ksp.common.FunctionUtils.isSuspend
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.writeTagValue
import ru.tinkoff.kora.ksp.common.KspCommonUtils.findRepeatableAnnotation
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.MappingData
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.ksp.common.parseAnnotationValue
import ru.tinkoff.kora.ksp.common.parseMappingData
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException

class ClientClassGenerator(private val resolver: Resolver) {

    fun generate(declaration: KSClassDeclaration): TypeSpec {
        val typeName = declaration.clientName()
        val methods: List<MethodData> = this.parseMethods(declaration)
        val builder = declaration.extendsKeepAop(typeName)
            .generated(ClientClassGenerator::class)
        if (hasAopAnnotations(declaration)) {
            builder.addModifiers(KModifier.OPEN)
        }
        builder.primaryConstructor(this.buildConstructor(builder, declaration, methods))
        builder.addProperty("rootUrl", String::class.asClassName(), KModifier.PRIVATE, KModifier.FINAL);

        for (method in methods) {
            builder.addProperty(method.declaration.simpleName.asString() + "Client", httpClient, KModifier.PRIVATE, KModifier.FINAL)
            builder.addProperty(method.declaration.simpleName.asString() + "RequestTimeout", Duration::class.asClassName().copy(true), KModifier.PRIVATE, KModifier.FINAL)
            builder.addProperty(method.declaration.simpleName.asString() + "UriTemplate", String::class, KModifier.PRIVATE, KModifier.FINAL);
            val hasUriParameters = method.parameters.any { it is Parameter.QueryParameter || it is Parameter.PathParameter }
            if (!hasUriParameters) {
                builder.addProperty(method.declaration.simpleName.asString() + "Uri", URI::class.asClassName(), KModifier.PRIVATE, KModifier.FINAL);
            }
            val funSpec: FunSpec = this.buildFunction(method)
            builder.addFunction(funSpec)
        }
        return builder.build()
    }

    private fun parseParametersConverters(methods: List<MethodData>): Map<String, ParameterizedTypeName> {
        val result = hashMapOf<String, ParameterizedTypeName>()
        for (method in methods) {
            for (parameter in method.parameters) {
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
                            parameterType = parameterType.arguments[0].type?.resolve() ?: continue
                        } else if(parameterType.isMap()) {
                            parameterType = parameterType.arguments[1].type?.resolve() ?: continue
                        }

                        if (requiresConverter(parameterType)) {
                            result[getConverterName(method, parameter.parameter)] = getConverterTypeName(parameterType)
                        }
                    }

                    is Parameter.HeaderParameter -> {
                        var parameterType = parameter.parameter.type.resolve()
                        if (parameterType.isCollection()) {
                            parameterType = parameterType.arguments[0].type?.resolve() ?: continue
                        } else if(parameterType.isMap()) {
                            parameterType = parameterType.arguments[1].type?.resolve() ?: continue
                        }

                        if (requiresConverter(parameterType) && httpHeaders != parameterType.toClassName()) {
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

        val httpRoute = method.findAnnotation(HttpClientClassNames.httpRoute)!!
        val httpMethod = httpRoute.findValueNoDefault<String>("method")!!
        b.addStatement("val _client = this.%L", method.simpleName.asString() + "Client");
        b.addStatement("val _headers = %T.of()", httpHeaders);
        b.addStatement("val _uriTemplate = this.%L", method.simpleName.asString() + "UriTemplate");
        b.addStatement("val _requestTimeout = this.%L", method.simpleName.asString() + "RequestTimeout");

        val hasPathParameters = methodData.parameters.any { it is Parameter.PathParameter }
        val hasQueryParameters = methodData.parameters.any { it is Parameter.QueryParameter }
        val bodyParameter = methodData.parameters.filterIsInstance<Parameter.BodyParameter>().firstOrNull()

        if (hasPathParameters || hasQueryParameters) {
            val httpPath = httpRoute.findValueNoDefault<String>("path")!!
            val uriWithPlaceholdersString: String
            if (hasPathParameters) {
                b.add("val _uriNoQuery = this.rootUrl\n");
                val parts = this.parseRouteParts(httpPath, methodData.parameters);
                val uriWithPlaceholdersStringB = StringBuilder();
                for (routePart in parts) {
                    if (routePart.string != null) {
                        uriWithPlaceholdersStringB.append(routePart.string)
                        b.add("  .plus(%S)\n", routePart.string)
                    } else {
                        uriWithPlaceholdersStringB.append("placeholder");
                        if (requiresConverter(routePart.parameter!!.parameter.type.resolve())) {
                            val converterName = getConverterName(methodData, routePart.parameter.parameter);
                            b.add(
                                "  .plus(%T.encode(%L.convert(%N), %T.UTF_8))\n",
                                URLEncoder::class.asClassName(),
                                converterName,
                                routePart.parameter.parameter.name?.asString(),
                                StandardCharsets::class.asClassName()
                            );
                        } else {
                            b.add(
                                "  .plus(%T.encode(%N.toString(), %T.UTF_8))\n",
                                URLEncoder::class.asClassName(),
                                routePart.parameter.parameter.name?.asString(),
                                StandardCharsets::class.asClassName()
                            );
                        }
                    }
                }
                uriWithPlaceholdersString = uriWithPlaceholdersStringB.toString();
                b.add(";\n");
            } else {
                b.addStatement("val _uriNoQuery = this.rootUrl + %S", httpPath);
                uriWithPlaceholdersString = httpPath;
            }
            if (!hasQueryParameters) {
                b.addStatement("val _uri = %T.create(_uriNoQuery)", URI::class.asClassName())
            } else {
                val uriWithPlaceholders = URI.create(uriWithPlaceholdersString);
                val hasQMark = uriWithPlaceholders.getQuery() != null;
                val hasFirstParam = hasQMark && !uriWithPlaceholders.getQuery().isBlank();
                b.addStatement("val _query = %T(%L, %L)", uriQueryBuilder, !hasQMark, hasFirstParam);

                methodData.parameters.filterIsInstance<Parameter.QueryParameter>().forEach {
                    var parameterType = it.parameter.type.resolve()
                    var literalName = it.parameter.name!!.asString()
                    val iterable = parameterType.isCollection()
                    val nullable = parameterType.isMarkedNullable

                    if (nullable) {
                        b.beginControlFlow("if (%N != null)", literalName)
                    }

                    if (parameterType.isMap()) {
                        val keyType = parameterType.arguments[0].type?.resolve()
                        if (keyType!!.toClassName() != String::class.asClassName()) {
                            throw ProcessingErrorException("@Query map key type must be String, but was: $keyType", method)
                        }

                        b.beginControlFlow("%L.forEach { _k, _v -> ", literalName)
                            .beginControlFlow("if(!_k.isNullOrBlank())")

                        val argType = parameterType.arguments[1].type?.resolve()!!
                        if(argType.isMarkedNullable) {
                            b.beginControlFlow("if(_v == null)")
                                .addStatement("_query.add(_k)")
                                .nextControlFlow("else")
                        }

                        if (!requiresConverter(argType)) {
                            b.addStatement("_query.add(_k, _v.toString())")
                        } else {
                            b.addStatement("_query.add(_k, %L.convert(_v))", getConverterName(methodData, it.parameter))
                        }

                        if(argType.isMarkedNullable) {
                            b.endControlFlow()
                        }
                        b.endControlFlow().endControlFlow()
                    } else {
                        if (iterable) {
                            val argType = parameterType.arguments[0].type?.resolve()
                            val iteratorName = literalName + "_iterator"
                            val paramName = "_" + literalName + "_element"
                            b.addStatement("val %N = %N.iterator()", iteratorName, literalName)
                                .beginControlFlow("if (!%N.hasNext())", iteratorName)
                                .addStatement("_query.unsafeAdd(%S)", URLEncoder.encode(it.queryParameterName, StandardCharsets.UTF_8))
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
                            b.add(
                                "_query.unsafeAdd(%S, %T.encode(%N.toString(), %T.UTF_8))\n",
                                URLEncoder.encode(it.queryParameterName, StandardCharsets.UTF_8),
                                URLEncoder::class.asClassName(),
                                literalName,
                                StandardCharsets::class.asClassName()
                            )
                        } else {
                            b.add(
                                "_query.unsafeAdd(%S, %T.encode(%L.convert(%L), %T.UTF_8))\n",
                                URLEncoder.encode(it.queryParameterName, StandardCharsets.UTF_8),
                                URLEncoder::class.asClassName(),
                                getConverterName(methodData, it.parameter),
                                literalName,
                                StandardCharsets::class.asClassName()
                            )
                        }

                        if (iterable) {
                            b.add(CodeBlock.builder().unindent().build()).add("\n")
                                .add("} while (%N.hasNext())", it.parameter.name!!.asString() + "_iterator")
                            b.endControlFlow()
                        }
                    }

                    if (nullable) {
                        b.endControlFlow()
                    }
                }
                b.addStatement("val _uri = %T.create(_uriNoQuery + _query.build())", URI::class.asClassName());
            }
        } else {
            b.addStatement("val _uri = this.%L", method.simpleName.asString() + "Uri");
        }
        b.add("\n")
        methodData.parameters.filterIsInstance<Parameter.HeaderParameter>().forEach {
            var parameterType = it.parameter.type.resolve()
            var literalName = it.parameter.name!!.asString()
            val iterable = parameterType.isCollection()
            val nullable = parameterType.isMarkedNullable
            if (nullable) {
                b.beginControlFlow("if (%N != null)", it.parameter.name?.asString().toString())
            }

            if(httpHeaders == parameterType.toClassName()) {
                b.beginControlFlow("%L.forEach { _e -> ", literalName)
                b.addStatement("_headers.add(_e.key, _e.value)", getConverterName(methodData, it.parameter))
                b.endControlFlow()
            } else if (parameterType.isMap()) {
                val keyType = parameterType.arguments[0].type?.resolve()
                if (keyType!!.toClassName() != String::class.asClassName()) {
                    throw ProcessingErrorException("@Header map key type must be String, but was: $keyType", method)
                }

                b.beginControlFlow("%L.forEach { _k, _v -> ", literalName)
                    .beginControlFlow("if(!_k.isNullOrBlank())")

                val argType = parameterType.arguments[1].type?.resolve()!!
                if(argType.isMarkedNullable) {
                    b.beginControlFlow("if(_v != null)")
                }

                if (!requiresConverter(argType)) {
                    b.addStatement("_headers.add(_k, _v.toString())")
                } else {
                    b.addStatement("_headers.add(_k, %L.convert(_v))", getConverterName(methodData, it.parameter))
                }

                if(argType.isMarkedNullable) {
                    b.endControlFlow()
                }
                b.endControlFlow().endControlFlow()
            } else {
                if (iterable) {
                    val argType = parameterType.arguments[0].type?.resolve()
                    val iteratorName = literalName + "_iterator"
                    val paramName = "_" + literalName + "_element"
                    b.addStatement("val %N = %N.iterator()", iteratorName, literalName)
                        .beginControlFlow("while (%N.hasNext())", it.parameter.name!!.asString() + "_iterator")
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
                    b.addStatement("_headers.add(%S, %N.toString())", it.headerName, literalName)
                } else {
                    b.addStatement("_headers.add(%S, %N.convert(%N))", it.headerName, getConverterName(methodData, it.parameter), literalName)
                }

                if (iterable) {
                    b.endControlFlow().add("\n")
                }
            }

            if (nullable) {
                b.endControlFlow()
            }
        }
        if (bodyParameter == null) {
            b.addStatement("val _body = %T.empty()", httpBody)
        } else {
            val requestMapperName = method.simpleName.asString() + "RequestMapper";
            b.add("val _body = ").controlFlow("try") {
                addStatement("this.%N.apply(%T.current(), %N)", requestMapperName, CommonClassNames.context, bodyParameter.parameter.name?.asString())
                nextControlFlow("catch (_e: %T)", httpClientException)
                addStatement("throw _e")
                nextControlFlow("catch (_e: Exception)")
                addStatement("throw %T(_e)", httpClientEncoderException)
            }
        }

        b.addStatement("val _request = %T.of(%S, _uri, _uriTemplate, _headers, _body, _requestTimeout)", httpClientRequest, httpMethod);

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
            b.add("    throw %T(it)\n", httpClientUnknownException)
            b.add("  }\n")
            b.add("  throw %T(_e)\n", httpClientUnknownException)
            b.add("} catch (_e: %T) {\n", RuntimeException::class.asClassName())
            b.add("  throw _e\n")
            b.add("} catch (_e: Exception) {\n")
            b.add("  throw %T(_e)\n", httpClientUnknownException)
            b.add("}\n")
        }
        return m.addCode(b.build()).build()
    }


    private fun parseRouteParts(httpPath: String, parameters: List<Parameter>): List<RoutePart> {
        var parts = listOf(RoutePart(null, httpPath))
        for (parameter in parameters) {
            val newList = arrayListOf<RoutePart>()
            if (parameter is Parameter.PathParameter) {
                for (part in parts) {
                    if (part.parameter != null) {
                        newList.add(part)
                    } else {
                        var from = 0
                        val token = "{" + parameter.pathParameterName + "}"
                        while (true) {
                            val idx = part.string!!.indexOf(token, from)
                            from = if (idx < 0) {
                                val str = part.string.substring(from)
                                if (str.isNotEmpty()) {
                                    newList.add(RoutePart(null, str))
                                }
                                break
                            } else {
                                val str = part.string.substring(from, idx)
                                if (str.isNotEmpty()) {
                                    newList.add(RoutePart(null, str))
                                }
                                newList.add(RoutePart(parameter, null))
                                idx + token.length
                            }
                        }
                    }
                }
                parts = newList
            }
        }
        return parts
    }

    private class RoutePart(val parameter: Parameter.PathParameter?, val string: String?) {
        init {
            check(!(parameter != null && string != null))
        }
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
        builder.addStatement("this.rootUrl = config.url()")

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
                "val %L = config.apply(httpClient, %T::class.java, %S, config.%L(), telemetryFactory, %S)\n",
                name,
                declaration.toClassName(),
                name,
                name,
                method.findAnnotation(httpRoute)!!.findValueNoDefault<String>("path")!!
            )
            builder.addStatement("this.%LUriTemplate = %L.url()", name, name)
            val hasUriParameters = methodData.parameters.any { it is Parameter.QueryParameter || it is Parameter.PathParameter }
            if (!hasUriParameters) {
                builder.addCode("this.%LUri = %T.create(%L.url());\n", name, URI::class.asClassName(), name)
            }
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
            val code = mapper.findValueNoDefault<Int>("code")!!
            if (type == null && mapperType == null) {
                return@map ResponseCodeMapperData(code, null, null, true)
            }
            val isAssignable = when {
                type != null -> declaration.returnType!!.resolve().isAssignableFrom(type)
                mapperType != null -> {
                    val supertype = mapperType.findSupertype(httpClientResponseMapper)!!
                    var typeArg = supertype.arguments[0].type!!.resolve()
                    if (typeArg.isCompletionStage()) {
                        typeArg = typeArg.arguments[0].type!!.resolve()
                    }
                    typeArg.declaration is KSTypeParameter || declaration.returnType!!.resolve().isAssignableFrom(typeArg)
                }

                else -> throw IllegalStateException()
            }

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
            httpClientResponseMapper.parameterizedBy(
                CompletionStage::class.asClassName().parameterizedBy(
                    returnType.toTypeName()
                )
            )
        } else {
            httpClientResponseMapper.parameterizedBy(returnType.toTypeName())
        }
    }

    data class ResponseCodeMapperData(val code: Int, val type: KSType?, val mapper: KSType?, val assignable: Boolean) {
        fun responseMapperType(returnType: KSType, suspend: Boolean): TypeName {
            if (type != null) {
                val typeName = type.toTypeName().copy(nullable = false)
                if (suspend) {
                    return httpClientResponseMapper.parameterizedBy(CompletionStage::class.asClassName().parameterizedBy(typeName))
                } else {
                    return httpClientResponseMapper.parameterizedBy(typeName)
                }
            } else if (mapper != null) {
                if (mapper.declaration is KSClassDeclaration && mapper.declaration.typeParameters.isNotEmpty()) {
                    val typeArg = returnType.toTypeName().copy(false)
                    return mapper.toClassName().parameterizedBy(mapper.declaration.typeParameters.map { typeArg })
                }
                return mapper.toTypeName()
            } else {
                val returnTypeName = returnType.toTypeName().copy(nullable = false)
                if (suspend) {
                    return httpClientResponseMapper.parameterizedBy(CompletionStage::class.asClassName().parameterizedBy(returnTypeName))
                } else {
                    return httpClientResponseMapper.parameterizedBy(returnTypeName)
                }
            }
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
