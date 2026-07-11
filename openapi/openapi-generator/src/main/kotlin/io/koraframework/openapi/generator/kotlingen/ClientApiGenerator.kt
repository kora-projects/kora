package io.koraframework.openapi.generator.kotlingen

import com.squareup.kotlinpoet.*
import org.apache.commons.lang3.StringUtils
import io.koraframework.openapi.generator.CodegenParams
import io.koraframework.openapi.generator.CodegenParams.ClientResponseMode.SUCCESSFUL
import org.openapitools.codegen.CodegenOperation
import org.openapitools.codegen.model.OperationsMap
import kotlin.text.get

class ClientApiGenerator() : AbstractKotlinGenerator<OperationsMap>() {
    override fun generate(ctx: OperationsMap): FileSpec {
        val b = TypeSpec.interfaceBuilder(ctx["classname"] as String)
            .addAnnotation(generated())
            .addAnnotation(buildHttpClientAnnotation(ctx))
        for (operation in ctx.operations.operation) {
            b.addFunction(buildFunction(ctx, operation))
            if (usesSuccessfulResponseMapper(ctx, operation)) {
                b.addType(buildResponseException(ctx, operation))
            }
            if (operation.hasFormParams) {
                b.addType(buildFormParamsRecord(ctx, operation))
            }
        }

        return FileSpec.get(apiPackage, b.build())
    }

    private fun buildFunction(ctx: OperationsMap, operation: CodegenOperation): FunSpec {
        val b = FunSpec.builder(operation.operationId)
            .addModifiers(KModifier.ABSTRACT)
            .addKdoc(buildFunctionKdoc(ctx, operation))
        buildAdditionalMethodAnnotations(ctx, operation).forEach { b.addAnnotation(it) }
        b.addAnnotations(this.buildImplicitHeaders(operation))
        b.addAnnotation(buildRouteAnnotation(operation))
        val clientMapping = clientMapping(ctx, operation)
        if (clientMapping != null) {
            b.addAnnotation(
                AnnotationSpec.builder(Classes.mapping.asKt())
                    .addMember("value = %T::class", com.palantir.javapoet.ClassName.bestGuess(clientMapping.type()).asKt())
                    .build()
            )
        } else if (usesSuccessfulResponseMapper(ctx, operation)) {
            b.addAnnotation(
                AnnotationSpec.builder(Classes.mapping.asKt())
                    .addMember("value = %T::class", ClassName(apiPackage, ctx.get("classname").toString() + "ClientResponseMappers", StringUtils.capitalize(operation.operationId) + "SuccessfulResponseMapper"))
                    .build()
            )
        } else {
            for (response in operation.responses) {
                b.addAnnotation(
                    AnnotationSpec.builder(Classes.responseCodeMapper.asKt())
                        .addMember("code = %L", if (response.isDefault) "-1" else response.code)
                        .addMember(
                            "mapper = %T::class",
                            ClassName(apiPackage, ctx.get("classname").toString() + "ClientResponseMappers", (StringUtils.capitalize(operation.operationId) + response.code) + "ApiResponseMapper")
                        )
                        .build()
                )
            }
        }
//        this.buildMethodAuth(operation, Classes.httpClientInterceptor.asKt())?.let {
//            b.addAnnotation(it)
//        }
        b.addAnnotations(this.buildInterceptors(ctx, operation, Classes.httpClientInterceptor.asKt()))
        if (operation.isDeprecated) {
            b.addAnnotation(AnnotationSpec.builder(Deprecated::class.asClassName()).addMember("%S", "deprecated").build())
        }
        b.returns(clientReturnType(ctx, operation))
        if (operation.hasAuthMethods && params.authAsMethodArgument) {
            b.addParameter(this.buildAuthParameter(operation));
        }
        if (hasBareObjectBody(operation)) {
            b.addParameter(httpHeadersParameter())
        }
        for (param in operation.allParams) {
            if (param.isFormParam) {
                continue  // form params are handled separately
            }
            if (param.isHeaderParam && operation.implicitHeadersParams != null && operation.implicitHeadersParams.stream().anyMatch({ h -> h.paramName.equals(param.paramName) })) {
                continue
            }
            b.addParameter(this.buildParameter(ctx, operation, param))
        }
        if (operation.hasFormParams) {
            val className = ClassName(
                apiPackage, ctx.get("classname") as String, StringUtils.capitalize(operation.operationId) + "FormParam"
            )
            val mapper = ClassName(
                apiPackage, ctx.get("classname") as String + "ClientRequestMappers", StringUtils.capitalize(operation.operationId) + "FormParamRequestMapper"
            )
            val parameter = ParameterSpec.builder("form", className)
                .addAnnotation(
                    AnnotationSpec.builder(Classes.mapping.asKt())
                        .addMember("value = %T::class", mapper)
                        .build()
                )
                .build()
            b.addParameter(parameter)
        }
        return b.build()
    }

    private fun clientMapping(ctx: OperationsMap, operation: CodegenOperation): CodegenParams.ClientMapping? {
        var result: CodegenParams.ClientMapping? = null
        for (extension in resolveExtensions(ctx, operation)) {
            extension.clientMapping()?.let {
                result = it
            }
        }
        return result
    }

    private fun httpHeadersParameter(): ParameterSpec {
        return ParameterSpec.builder("additionalHeaders", Classes.httpHeaders.asKt())
            .addAnnotation(AnnotationSpec.builder(Classes.header.asKt()).build())
            .build()
    }

    private fun clientReturnType(ctx: OperationsMap, operation: CodegenOperation): TypeName {
        val responseClassName = ClassName(apiPackage, ctx.get("classname").toString() + "Responses", StringUtils.capitalize(operation.operationId) + "ApiResponse")
        if (params.clientResponseMode != SUCCESSFUL) {
            return responseClassName
        }
        val successfulResponses = operation.responses
            .filter { !it.isDefault }
            .filter {
                val code = it.code.toInt()
                code >= 200 && code < 300
            }
        if (successfulResponses.size == 1) {
            val response = successfulResponses.first()
            return if (operation.responses.size == 1)
                responseClassName
            else
                responseClassName.nestedClass(StringUtils.capitalize(operation.operationId) + response.code + "ApiResponse")
        }
        if (successfulResponses.size > 1) {
            val dataType = successfulResponses.first().dataType
            if (dataType != null && successfulResponses.all { dataType == it.dataType }) {
                return responseClassName.nestedClass(responseClassName.simpleName.removeSuffix("ApiResponse") + sanitizeSharedResponseName(dataType))
            }
        }
        return responseClassName
    }

    private fun usesSuccessfulResponseMapper(ctx: OperationsMap, operation: CodegenOperation): Boolean {
        if (params.clientResponseMode != SUCCESSFUL || !hasErrorResponses(operation)) {
            return false
        }
        return clientReturnType(ctx, operation) != fullResponseType(ctx, operation)
    }

    private fun hasErrorResponses(operation: CodegenOperation): Boolean {
        return operation.responses.any {
            if (it.isDefault) {
                true
            } else {
                val code = it.code.toInt()
                code < 200 || code >= 300
            }
        }
    }

    private fun fullResponseType(ctx: OperationsMap, operation: CodegenOperation): ClassName {
        return ClassName(apiPackage, ctx.get("classname").toString() + "Responses", StringUtils.capitalize(operation.operationId) + "ApiResponse")
    }

    private fun buildResponseException(ctx: OperationsMap, operation: CodegenOperation): TypeSpec {
        val responseType = fullResponseType(ctx, operation)
        val constructor = FunSpec.constructorBuilder()
            .addParameter("code", INT)
            .addParameter("headers", Classes.httpHeaders.asKt())
            .addParameter("response", responseType)
            .build()
        return TypeSpec.classBuilder(responseExceptionSimpleName(ctx, operation))
            .addAnnotation(generated())
            .superclass(Classes.httpClientResponseException.asKt())
            .addSuperclassConstructorParameter("code")
            .addSuperclassConstructorParameter("headers")
            .addSuperclassConstructorParameter("ByteArray(0)")
            .primaryConstructor(constructor)
            .addProperty(PropertySpec.builder("response", responseType).initializer("response").build())
            .build()
    }

    private fun sanitizeSharedResponseName(dataType: String): String {
        val name = dataType.replace(Regex("[^a-zA-Z0-9]"), "")
        return if (name.isBlank()) "ContentApiResponse" else StringUtils.capitalize(name) + "ApiResponse"
    }

    companion object {
        fun responseExceptionSimpleName(ctx: OperationsMap, operation: CodegenOperation): String {
            return ctx.get("classname").toString() + StringUtils.capitalize(operation.operationId) + "HttpClientResponseException"
        }
    }

    private fun buildAuthParameter(op: CodegenOperation): ParameterSpec {
        val authMethod = op.authMethods.asSequence()
            .filter { a -> params.primaryAuth == null || a.name.equals(params.primaryAuth) }
            .firstOrNull()
            ?: throw IllegalArgumentException("Can't find OpenAPI securitySchema named: " + params.primaryAuth)

        fun getAuthName(name: String): String {
            for (parameter in op.allParams) {
                if (name == parameter.paramName) {
                    return getAuthName("_$name")
                }
            }
            return name
        }

        val authName = getAuthName(authMethod.name)
        val p = ParameterSpec.builder(authName, String::class.asClassName().copy(nullable = true))
        if (authMethod.isKeyInQuery) {
            return p.addAnnotation(
                AnnotationSpec.builder(Classes.query.asKt())
                    .addMember("value = %S", authMethod.keyParamName)
                    .build()
            )
                .build()
        }
        if (authMethod.isKeyInHeader) {
            return p.addAnnotation(
                AnnotationSpec.builder(Classes.header.asKt())
                    .addMember("value = %S", authMethod.keyParamName)
                    .build()
            )
                .build()
        }
        if (authMethod.isKeyInCookie) {
            return p.addAnnotation(
                AnnotationSpec.builder(Classes.cookie.asKt())
                    .addMember("value = %S", authMethod.keyParamName)
                    .build()
            )
                .build()
        }
        if (authMethod.isOAuth || authMethod.isOpenId || authMethod.isBasicBearer || authMethod.isBasic || authMethod.isBasicBasic) {
            for (parameter in op.headerParams) {
                require(!"Authorization".equals(parameter.paramName, ignoreCase = true)) {
                    "Authorization argument as method parameter can't be set, cause parameter named 'Authorization' already is present"
                }
            }
            return p.addAnnotation(
                AnnotationSpec.builder(Classes.header.asKt())
                    .addMember("value = %S", "Authorization")
                    .build()
            )
                .build()
        }
        throw IllegalStateException("Auth argument can be in Query, Header or Cookie, but was unknown")
    }

    private fun buildHttpClientAnnotation(ctx: OperationsMap): AnnotationSpec {
        val httpClientAnnotation = AnnotationSpec.builder(Classes.httpClient.asKt())
        params.clientConfigPrefix?.let { clientConfigPrefix ->
            val configPath = clientConfigPrefix + "." + StringUtils.uncapitalize(ctx.get("classname").toString())
            httpClientAnnotation.addMember("value = %S", configPath)
        } ?: params.clientConfig?.let { clientConfig ->
            httpClientAnnotation.addMember("value = %S", clientConfig)
        }
        val tag = ctx.get("baseName").toString()
        val clientTag = params.clientTags[tag]
        val defaultTag = params.clientTags["*"]
        if (clientTag != null && clientTag.httpClientTag() != null) {
            httpClientAnnotation.addMember("httpClientTag = %L::class", clientTag.httpClientTag()!!)
        } else if (defaultTag != null && defaultTag.httpClientTag() != null) {
            httpClientAnnotation.addMember("httpClientTag = %L::class", defaultTag.httpClientTag()!!)
        }
        if (clientTag != null && clientTag.telemetryTag() != null) {
            httpClientAnnotation.addMember("telemetryTag = %L::class", clientTag.telemetryTag()!!)
        } else if (defaultTag != null && defaultTag.httpClientTag() != null) {
            httpClientAnnotation.addMember("telemetryTag = %L::class", defaultTag.telemetryTag()!!)
        }
        return httpClientAnnotation.build()
    }
}
