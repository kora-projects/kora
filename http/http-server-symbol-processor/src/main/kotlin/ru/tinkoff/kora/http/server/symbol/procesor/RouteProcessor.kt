package ru.tinkoff.kora.http.server.symbol.procesor

import com.google.devtools.ksp.*
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.common.Context
import ru.tinkoff.kora.common.Mapping
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.http.common.annotation.Header
import ru.tinkoff.kora.http.common.annotation.HttpRoute
import ru.tinkoff.kora.http.common.annotation.Path
import ru.tinkoff.kora.http.common.annotation.Query
import ru.tinkoff.kora.http.server.common.HttpServerRequestHandler
import ru.tinkoff.kora.http.server.common.handler.*
import ru.tinkoff.kora.ksp.common.KspCommonUtils.findRepeatableAnnotation
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.parseAnnotationValue
import ru.tinkoff.kora.ksp.common.parseMappingData


@OptIn(KspExperimental::class)
class RouteProcessor(private val resolver: Resolver) {
    private val listErasure = resolver.getClassDeclarationByName(List::class.qualifiedName!!)!!.asStarProjectedType()
    private val uuidType = resolver.getClassDeclarationByName("java.util.UUID")!!.asType(listOf())
    private val responseMapperType = HttpServerResponseMapper::class.qualifiedName?.let { className -> resolver.getClassDeclarationByName(className)?.asStarProjectedType() }
    private val monoMemberName = MemberName("kotlinx.coroutines.reactor", "mono")
    private val awaitSingleOrNull = MemberName("kotlinx.coroutines.reactor", "awaitSingleOrNull")
    private val awaitSingle = MemberName("kotlinx.coroutines.reactor", "awaitSingle")
    private val requestMapper = HttpServerRequestMapper::class.asClassName()
    private val stringParameterReader = StringParameterReader::class.asClassName()
    private val interceptWithClassName = ClassName("ru.tinkoff.kora.http.common.annotation", "InterceptWith")
    private val interceptWithContainerClassName = ClassName("ru.tinkoff.kora.http.common.annotation", "InterceptWith", "InterceptWithContainer")
    private val dispatchersClassName = ClassName("kotlinx.coroutines", "Dispatchers")


    data class RequestMappingData(val method: String, val pathTemplate: String)

    @OptIn(KspExperimental::class)
    internal fun buildHttpRouteFunction(declaration: KSClassDeclaration, rootPath: String, function: KSFunctionDeclaration): FunSpec.Builder {
        val requestMappingData = extractRequestMappingData(rootPath, function)
        val parent = function.parent as KSClassDeclaration
        val funName = funName(requestMappingData)
        val returnType = function.returnType!!.resolve()
        val interceptors = sequenceOf(
            declaration.findRepeatableAnnotation(interceptWithClassName, interceptWithContainerClassName),
            function.findRepeatableAnnotation(interceptWithClassName, interceptWithContainerClassName)
        )
            .flatMap { it }
            .map { parseInterceptor(it) }
            .distinct()
            .toList()

        val mapperClassName = HttpServerResponseMapper::class.asClassName().parameterizedBy(function.returnType!!.toTypeName())
        val funBuilder = FunSpec.builder(funName)
            .returns(HttpServerRequestHandler::class)
            .addParameter(
                ParameterSpec.builder(
                    "_controller",
                    parent.toClassName()
                ).build()
            )
        val awaitCode = if (returnType.isMarkedNullable) awaitSingleOrNull else awaitSingle
        val mapping = function.parseMappingData().getMapping(responseMapperType!!)
        if (mapping != null) {
            val responseMapperType = if (mapping.mapper != null) mapping.mapper!!.toTypeName() else mapperClassName
            val b = ParameterSpec.builder("_responseMapper", responseMapperType)
            mapping.toTagAnnotation()?.let {
                b.addAnnotation(it)
            }
            funBuilder.addParameter(b.build())
        } else {
            funBuilder.addParameter(ParameterSpec.builder("_responseMapper", mapperClassName).build())
        }


        val isSuspend = function.modifiers.contains(Modifier.SUSPEND)
        val isBlocking = !isSuspend
        funBuilder.controlFlow(
            "return %T.%L(%S) { _request ->",
            HttpServerRequestHandlerImpl::class,
            requestMappingData.method.lowercase(),
            requestMappingData.pathTemplate,
        ) {
            var requestName = "_request"
            for (i in interceptors.indices) {
                val interceptor = interceptors[i]
                val interceptorName = "_interceptor" + (i + 1)
                val newRequestName = "_request" + (i + 1)
                funBuilder.beginControlFlow("%L.intercept(%L) { %L ->", interceptorName, requestName, newRequestName)
                requestName = newRequestName
                val builder = ParameterSpec.builder(interceptorName, interceptor.type)
                if (interceptor.tag != null) {
                    builder.addAnnotation(interceptor.tag)
                }
                funBuilder.addParameter(builder.build())
            }
            funBuilder.controlFlow(" %M(%T.Unconfined + %T.Kotlin.asCoroutineContext(%T.current())) { ", monoMemberName, dispatchersClassName, Context::class, Context::class) {
                val params = generateFunctionParameters(requestName, function, funBuilder)
                if (isBlocking) {
                    funBuilder.addParameter("_executor", BlockingRequestExecutor::class)
                    funBuilder.addStatement(
                        "val response = _executor.execute{ _controller.%L(%L)}.%M()",
                        function.simpleName.asString(),
                        params,
                        awaitCode,
                    )
                    funBuilder.addStatement("_responseMapper.apply(response).%M()", awaitCode)
                } else {
                    funBuilder.addStatement("val response = _controller.%L(%L)", function.simpleName.asString(), params)
                    funBuilder.addStatement("_responseMapper.apply(response).%M()", awaitCode)
                }
            }
        }


        for (i in interceptors) {
            funBuilder.endControlFlow()
        }

        return funBuilder
    }

    private fun generateFunctionParameters(
        requestName: String,
        function: KSFunctionDeclaration,
        funBuilder: FunSpec.Builder
    ): String {
        for (param in function.parameters) {
            generateParameterDeclaration(requestName, param, funBuilder)
        }
        return function.parameters.joinToString(",") { it.name!!.asString() }

    }

    @OptIn(KspExperimental::class)
    private fun generateParameterDeclaration(
        requestName: String,
        param: KSValueParameter,
        funBuilder: FunSpec.Builder
    ) {
        when {
            param.isAnnotationPresent(Query::class) -> {
                val name = param.getAnnotationsByType(Query::class).first().value
                addExtractorToFunction("Query", name, param, funBuilder)
            }

            param.isAnnotationPresent(Header::class) -> {
                val name = param.getAnnotationsByType(Header::class).first().value
                addExtractorToFunction("Header", name, param, funBuilder)
            }

            param.isAnnotationPresent(Path::class) -> {
                val name = param.getAnnotationsByType(Path::class).first().value
                addExtractorToFunction("Path", name, param, funBuilder)
            }

            else -> {
                generateMappedParamDeclaration(requestName, param, funBuilder)
            }
        }
    }

    @OptIn(KspExperimental::class)
    private fun extractRequestMappingData(rootPath: String, declaration: KSFunctionDeclaration): RequestMappingData {
        val httpRoute = declaration.getAnnotationsByType(HttpRoute::class).first()
        return RequestMappingData(httpRoute.method, "$rootPath${httpRoute.path}")
    }

    private fun funName(requestMappingData: RequestMappingData): String {
        val methodName = requestMappingData.method.lowercase() + requestMappingData.pathTemplate.split(Regex("[^A-Za-z0-9]+"))
            .filter { it.isNotBlank() }
            .joinToString("_", "_")
        return methodName
    }

    private fun generateParamDeclaration(
        extractorFunctionData: ExtractorFunction,
        name: String,
        valueParameter: KSValueParameter
    ): CodeBlock {
        val requiredName = name.ifBlank { valueParameter.name!!.asString() }

        return CodeBlock.of(
            "val %L = %M(_request, %S)\n",
            valueParameter.name!!.asString(),
            extractorFunctionData.memberName,
            requiredName
        )
    }

    private fun generateMappedParamDeclaration(requestName: String, valueParameter: KSValueParameter, funBuilder: FunSpec.Builder) {
        val paramName = valueParameter.name?.asString()
        val mappedAnnotation = valueParameter.getAnnotationsByType(Mapping::class).toList().firstOrNull()
        val tag = valueParameter.getAnnotationsByType(Tag::class).toList().firstOrNull()

        val isNullable = valueParameter.type.resolve().nullability == Nullability.NULLABLE
        val awaitCode = if (isNullable) awaitSingleOrNull else awaitSingle
        val mapperType = try {
            mappedAnnotation?.value?.asTypeName() ?: requestMapper.parameterizedBy(valueParameter.type.toTypeName())
        } catch (e: KSTypeNotPresentException) {
            e.ksType.toTypeName()
        }
        val mapperParameter = ParameterSpec.builder("_${paramName}Mapper", mapperType)
        if (tag != null) {
            val tagAnnotation = AnnotationSpec.builder(Tag::class.asClassName())
            val tagTypes = try {
                tag.value.map { it.asClassName() }
            } catch (e: KSTypesNotPresentException) {
                e.ksTypes.map { it.toClassName() }
            }
            tagTypes.forEach {
                tagAnnotation.addMember("%T::class", it)
            }
            mapperParameter.addAnnotation(tagAnnotation.build())
        }
        funBuilder.addParameter(mapperParameter.build())
        funBuilder.addCode("val $paramName = _${paramName}Mapper.apply(%N).%M()\n", requestName, awaitCode)
    }


    private fun addExtractorToFunction(paramType: String, name: String, valueParameter: KSValueParameter, funBuilder: FunSpec.Builder) {
        val typeArguments = valueParameter.type.element?.typeArguments ?: emptyList()
        val parameterTypeRef = valueParameter.type
        val parameterType = parameterTypeRef.resolve()
        if (listErasure.isAssignableFrom(parameterType) || listErasure.makeNullable().isAssignableFrom(parameterType)) {
            val listTypeArgument = typeArguments.first().type!!
            when (val arg = listTypeArgument.resolve()) {
                resolver.builtIns.intType, resolver.builtIns.intType.makeNullable() -> {
                    if (paramType == "Query") {
                        funBuilder.addCode(generateParamDeclaration(ExtractorFunction.LIST_INT_QUERY, name, valueParameter))
                        return
                    }
                }

                resolver.builtIns.stringType, resolver.builtIns.stringType.makeNullable() -> {
                    when (paramType) {
                        "Query" -> {
                            funBuilder.addCode(generateParamDeclaration(ExtractorFunction.LIST_STRING_QUERY, name, valueParameter))
                            return
                        }

                        "Header" -> {
                            funBuilder.addCode(generateParamDeclaration(ExtractorFunction.LIST_STRING_HEADER, name, valueParameter))
                            return
                        }
                    }
                }

                resolver.builtIns.doubleType, resolver.builtIns.doubleType.makeNullable() -> {
                    if (paramType == "Query") {
                        funBuilder.addCode(generateParamDeclaration(ExtractorFunction.LIST_DOUBLE_QUERY, name, valueParameter))
                        return
                    }
                }

                resolver.builtIns.booleanType, resolver.builtIns.booleanType.makeNullable() -> {
                    if (paramType == "Query") {
                        funBuilder.addCode(generateParamDeclaration(ExtractorFunction.LIST_BOOLEAN_QUERY, name, valueParameter))
                        return
                    }
                }

                resolver.builtIns.longType, resolver.builtIns.longType.makeNullable() -> {
                    if (paramType == "Query") {
                        funBuilder.addCode(generateParamDeclaration(ExtractorFunction.LIST_LONG_QUERY, name, valueParameter))
                        return
                    }
                }

                else -> {
                    val parameterName = valueParameter.name!!.asString()
                    if (paramType == "Query") {
                        funBuilder.addParameter("_${parameterName}StringParameterReader", stringParameterReader.parameterizedBy(arg.toTypeName()))
                        funBuilder.addStatement(
                            "val %L = %M(_request, %S).map { _${parameterName}StringParameterReader.read(it) }",
                            parameterName,
                            ExtractorFunction.LIST_STRING_QUERY.memberName,
                            name
                        )
                        return
                    }
                    if (paramType == "Header") {
                        funBuilder.addParameter("_${parameterName}StringParameterReader", stringParameterReader.parameterizedBy(arg.toTypeName()))
                        funBuilder.addStatement(
                            "val %L = %M(_request, %S).map { _${parameterName}StringParameterReader.read(it) }",
                            parameterName,
                            ExtractorFunction.LIST_STRING_HEADER.memberName,
                            name
                        )
                        return
                    }
                }
            }
        }

        val isNullable = parameterType.isMarkedNullable
        when (parameterType) {
            resolver.builtIns.stringType, resolver.builtIns.stringType.makeNullable() -> {
                when (paramType) {
                    "Query" -> if (isNullable) {
                        funBuilder.addCode(generateParamDeclaration(ExtractorFunction.NULLABLE_STRING_QUERY, name, valueParameter))
                        return
                    } else {
                        funBuilder.addCode(generateParamDeclaration(ExtractorFunction.STRING_QUERY, name, valueParameter))
                        return
                    }

                    "Header" -> if (isNullable) {
                        funBuilder.addCode(generateParamDeclaration(ExtractorFunction.NULLABLE_STRING_HEADER, name, valueParameter))
                        return
                    } else {
                        funBuilder.addCode(generateParamDeclaration(ExtractorFunction.STRING_HEADER, name, valueParameter))
                        return
                    }

                    "Path" -> {
                        funBuilder.addCode(generateParamDeclaration(ExtractorFunction.STRING_PATH, name, valueParameter))
                        return
                    }
                }
            }

            resolver.builtIns.intType, resolver.builtIns.intType.makeNullable() -> {
                when (paramType) {
                    "Query" -> if (isNullable) {
                        funBuilder.addCode(generateParamDeclaration(ExtractorFunction.NULLABLE_INT_QUERY, name, valueParameter))
                        return
                    } else {
                        funBuilder.addCode(generateParamDeclaration(ExtractorFunction.INT_QUERY, name, valueParameter))
                        return
                    }

                    "Path" -> {
                        funBuilder.addCode(generateParamDeclaration(ExtractorFunction.INT_PATH, name, valueParameter))
                        return
                    }
                }
            }

            resolver.builtIns.longType, resolver.builtIns.longType.makeNullable() -> {
                when (paramType) {
                    "Query" -> if (isNullable) {
                        funBuilder.addCode(generateParamDeclaration(ExtractorFunction.NULLABLE_LONG_QUERY, name, valueParameter))
                        return
                    } else {
                        funBuilder.addCode(generateParamDeclaration(ExtractorFunction.LONG_QUERY, name, valueParameter))
                        return
                    }

                    "Path" -> {
                        funBuilder.addCode(generateParamDeclaration(ExtractorFunction.LONG_PATH, name, valueParameter))
                        return
                    }
                }
            }

            resolver.builtIns.doubleType, resolver.builtIns.doubleType.makeNullable() -> {
                when (paramType) {
                    "Query" -> if (isNullable) {
                        funBuilder.addCode(generateParamDeclaration(ExtractorFunction.NULLABLE_DOUBLE_QUERY, name, valueParameter))
                        return
                    } else {
                        funBuilder.addCode(generateParamDeclaration(ExtractorFunction.DOUBLE_QUERY, name, valueParameter))
                        return
                    }

                    "Path" -> {
                        funBuilder.addCode(generateParamDeclaration(ExtractorFunction.DOUBLE_PATH, name, valueParameter))
                        return
                    }
                }
            }

            resolver.builtIns.booleanType, resolver.builtIns.booleanType.makeNullable() -> {
                when (paramType) {
                    "Query" -> if (isNullable) {
                        funBuilder.addCode(generateParamDeclaration(ExtractorFunction.NULLABLE_BOOLEAN_QUERY, name, valueParameter))
                        return
                    } else {
                        funBuilder.addCode(generateParamDeclaration(ExtractorFunction.BOOLEAN_QUERY, name, valueParameter))
                        return
                    }
                }
            }

            uuidType -> {
                when (paramType) {
                    "Path" -> {
                        funBuilder.addCode(generateParamDeclaration(ExtractorFunction.UUID_PATH, name, valueParameter))
                        return
                    }
                }
            }
        }

        val parameterName = valueParameter.name!!.asString()
        val readerParameterName = "_${parameterName}StringParameterReader"
        funBuilder.addParameter(readerParameterName, stringParameterReader.parameterizedBy(parameterType.makeNotNullable().toTypeName()))

        when (paramType) {
            "Query" -> {
                if (isNullable) {
                    funBuilder.addStatement("val %L = %M(_request, %S)?.let(%L::read)", parameterName, ExtractorFunction.NULLABLE_STRING_QUERY.memberName, name, readerParameterName)
                } else {
                    funBuilder.addStatement("val %L = %L.read(%M(_request, %S))", parameterName, readerParameterName, ExtractorFunction.STRING_QUERY.memberName, name)
                }

                return
            }

            "Header" -> {
                if (isNullable) {
                    funBuilder.addStatement("val %L = %M(_request, %S)?.let(%L::read)", parameterName, ExtractorFunction.NULLABLE_STRING_HEADER.memberName, name, readerParameterName)
                } else {
                    funBuilder.addStatement("val %L = %L.read(%M(_request, %S))", parameterName, readerParameterName, ExtractorFunction.STRING_HEADER.memberName, name)
                }
            }

            "Path" -> {
                funBuilder.addStatement("val %L = %L.read(%M(_request, %S))", parameterName, readerParameterName, ExtractorFunction.STRING_PATH.memberName, name)
                return
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


