package io.koraframework.openapi.generator.kotlingen

import com.squareup.kotlinpoet.*
import io.koraframework.openapi.generator.CodegenParams
import io.koraframework.openapi.generator.SecurityData
import org.apache.commons.lang3.StringUtils
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
        if (!params.authAsMethodArgument) {
            val requirement = security.securityRequirementByOperation[operation.operationId]
            if (SecurityData.hasNonAnonymousRequirements(requirement)) {
                val interceptorTag = security.interceptorTagBySecurityRequirement[requirement]
                if (interceptorTag != null) {
                    b.addAnnotation(
                        AnnotationSpec.builder(Classes.interceptWith.asKt())
                            .addMember("value = %T::class", Classes.httpClientInterceptor.asKt())
                            .addMember("tag = %T::class", ClassName(apiPackage, "ApiSecurity", interceptorTag))
                            .build()
                    )
                }
            }
        }
        b.addAnnotations(this.buildInterceptors(ctx, operation, Classes.httpClientInterceptor.asKt()))
        if (operation.isDeprecated) {
            b.addAnnotation(AnnotationSpec.builder(Deprecated::class.asClassName()).addMember("%S", "deprecated").build())
        }
        b.returns(ClassName(apiPackage, ctx.get("classname").toString() + "Responses", StringUtils.capitalize(operation.operationId) + "ApiResponse"))
        if (operation.hasAuthMethods && params.authAsMethodArgument) {
            b.addParameter(this.buildAuthParameter(operation));
        }
        if (hasRawBodyHeaders(operation)) {
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

    private fun buildAuthParameter(op: CodegenOperation): ParameterSpec {
        val authMethod = op.authMethods.asSequence()
            .filter { a -> params.primaryAuth == null || a.name.equals(params.primaryAuth) }
            .firstOrNull()
            ?: throw IllegalArgumentException(missingPrimaryAuthError(op))

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
                    authorizationParameterConflictError(op)
                }
            }
            return p.addAnnotation(
                AnnotationSpec.builder(Classes.header.asKt())
                    .addMember("value = %S", "Authorization")
                    .build()
            )
                .build()
        }
        throw IllegalStateException(unsupportedAuthArgumentLocationError(op, authMethod.name))
    }

    private fun missingPrimaryAuthError(operation: CodegenOperation): String {
        return """
            Invalid OpenAPI generator `primaryAuth`: `${params.primaryAuth}`.

            Operation `${operation.operationId}` does not declare a matching security scheme.
            Available operation auth schemes: ${operation.authMethods.map { it.name }}

            Fix: set `primaryAuth` to one of the operation security scheme names, or remove `primaryAuth`.
        """.trimIndent()
    }

    private fun authorizationParameterConflictError(operation: CodegenOperation): String {
        return """
            Invalid OpenAPI operation `${operation.operationId}`: authorization parameter conflict.

            `authAsMethodArgument` needs to generate an `Authorization` header argument for the selected security scheme,
            but the operation already declares a header parameter named `Authorization`.

            Fix: remove or rename the explicit `Authorization` header parameter, or disable `authAsMethodArgument`.
        """.trimIndent()
    }

    private fun unsupportedAuthArgumentLocationError(operation: CodegenOperation, authName: String): String {
        return """
            Invalid OpenAPI security argument for operation `${operation.operationId}`.

            Security scheme `$authName` cannot be mapped to a generated method argument.
            Supported locations: query, header, cookie, or `Authorization` header for http/oauth/openId schemes.

            Fix: use a supported security scheme location, select another `primaryAuth`, or disable `authAsMethodArgument`.
        """.trimIndent()
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
