package ru.tinkoff.kora.openapi.generator.kotlingen

import com.squareup.kotlinpoet.*
import org.apache.commons.lang3.StringUtils
import org.openapitools.codegen.CodegenOperation
import org.openapitools.codegen.model.OperationsMap

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
        val tag = ctx.get("baseName").toString()
        val b = FunSpec.builder(operation.operationId)
            .addModifiers(KModifier.ABSTRACT)
            .addKdoc(buildFunctionKdoc(ctx, operation))
        buildAdditionalAnnotations(tag).forEach { b.addAnnotation(it) }
        b.addAnnotations(this.buildImplicitHeaders(operation))
        b.addAnnotation(buildRouteAnnotation(operation))
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
//        this.buildMethodAuth(operation, Classes.httpClientInterceptor.asKt())?.let {
//            b.addAnnotation(it)
//        }
        b.addAnnotations(this.buildInterceptors(tag, Classes.httpClientInterceptor.asKt()))
        if (operation.isDeprecated) {
            b.addAnnotation(AnnotationSpec.builder(Deprecated::class.asClassName()).addMember("%S", "deprecated").build())
        }
        b.returns(ClassName(apiPackage, ctx.get("classname").toString() + "Responses", StringUtils.capitalize(operation.operationId) + "ApiResponse"))
        if (operation.hasAuthMethods && params.authAsMethodArgument) {
            b.addParameter(this.buildAuthParameter(operation));
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
        params.clientConfigPrefix?.let {
            httpClientAnnotation.addMember("configPath = %S", it + "." + ctx.get("classname"))
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
