package ru.tinkoff.kora.openapi.generator.kotlingen

import com.squareup.kotlinpoet.*
import org.apache.commons.lang3.StringUtils
import org.openapitools.codegen.CodegenOperation
import org.openapitools.codegen.model.OperationsMap

class ServerApiGenerator() : AbstractKotlinGenerator<OperationsMap>() {
    override fun generate(ctx: OperationsMap): FileSpec {
        val b = TypeSpec.classBuilder(ctx["classname"] as String + "Controller")
            .addAnnotation(generated())
            .addAnnotation(Classes.component.asKt())
        if (params.prefixPath != null) {
            b.addAnnotation(AnnotationSpec.builder(Classes.httpController.asKt()).addMember("%S", params.prefixPath!!).build())
        } else {
            b.addAnnotation(AnnotationSpec.builder(Classes.httpController.asKt()).build())
        }
        val allowAspects = params.enableValidation || !params.additionalContractAnnotations.isEmpty()
        if (allowAspects) {
            b.addModifiers(KModifier.OPEN)
        }
        val delegate = ClassName(apiPackage, ctx.get("classname") as String + "Delegate")
        b.addProperty(PropertySpec.builder("delegate", delegate).initializer("delegate").build())
        b.primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter("delegate", delegate)
                .build()
        )
        for (operation in ctx.operations.operation) {
            b.addFunction(buildFunction(ctx, operation))
            if (operation.hasFormParams) {
                b.addType(buildFormParamsRecord(ctx, operation))
            }
        }
        return FileSpec.get(apiPackage, b.build())
    }

    private fun buildFunction(ctx: OperationsMap, operation: CodegenOperation): FunSpec {
        val tag = ctx["baseName"] as String
        val b = FunSpec.builder(operation.operationId)
            .addKdoc(buildFunctionKdoc(ctx, operation))
        val allowAspects = params.enableValidation || !params.additionalContractAnnotations.isEmpty()
        if (allowAspects) {
            b.addModifiers(KModifier.OPEN)
        }
        buildAdditionalAnnotations(tag).forEach { b.addAnnotation(it) }
        this.buildImplicitHeaders(operation).forEach { b.addAnnotation(it) }
        b.addAnnotation(buildRouteAnnotation(operation))
        buildMethodAuth(operation)?.let { auth ->
            b.addAnnotation(auth)
        }
        if (params.enableValidation) {
            b.addAnnotation(AnnotationSpec.builder(Classes.interceptWith.asKt()).addMember("value = %T::class", Classes.validationHttpServerInterceptor.asKt()).build())
            b.addAnnotation(AnnotationSpec.builder(Classes.validate.asKt()).build())
        }
        this.buildInterceptors(tag, Classes.httpServerInterceptor.asKt()).forEach(b::addAnnotation)
        b.addAnnotation(
            AnnotationSpec.builder(Classes.mapping.asKt())
                .addMember("value = %T::class", ClassName(apiPackage, ctx.get("classname") as String + "ServerResponseMappers", StringUtils.capitalize(operation.operationId) + "ApiResponseMapper"))
                .build()
        )
        b.addCode("return this.delegate.%N(", operation.operationId)
        var hasParams = false
        if (params.requestInDelegateParams) {
            hasParams = true
            b.addCode("_serverRequest")
            b.addParameter("_serverRequest", Classes.httpServerRequest.asKt())
        }
        for (param in operation.allParams) {
            if (param.isFormParam) {
                continue // form params are handled separately
            }
            if (param.isHeaderParam && operation.implicitHeadersParams != null && operation.implicitHeadersParams.any { it.paramName.equals(param.paramName) }) {
                continue
            }
            b.addParameter(this.buildParameter(ctx, operation, param))
            if (hasParams) {
                b.addCode(", ")
            }
            b.addCode("%N", param.paramName)
            hasParams = true
        }
        if (operation.hasFormParams) {
            val className = ClassName(
                apiPackage, ctx.get("classname") as String + "Controller", StringUtils.capitalize(operation.operationId) + "FormParam"
            )
            val mapper = ClassName(
                apiPackage, ctx.get("classname") as String + "ServerRequestMappers", StringUtils.capitalize(operation.operationId) + "FormParamRequestMapper"
            )
            val parameter = ParameterSpec.builder("form", className)
                .addAnnotation(
                    AnnotationSpec.builder(Classes.mapping.asKt())
                        .addMember("value = %T::class", mapper)
                        .build()
                )
                .build()
            b.addParameter(parameter)
            if (hasParams) {
                b.addCode(", ")
            }
            b.addCode("%N", parameter.name)
        }
        b.addCode(")\n")
        b.returns(ClassName(apiPackage, ctx.get("classname").toString() + "Responses", StringUtils.capitalize(operation.operationId) + "ApiResponse"))
        return b.build()
    }

    private fun buildMethodAuth(operation: CodegenOperation): AnnotationSpec? {
        val securityRequirement = security.securityRequirementByOperation[operation.operationId]
        if (securityRequirement.isNullOrEmpty()) {
            return null
        }
        val operationSecurityRequirement = security.securityRequirementByOperation[operation.operationId]
        val authTag = security.interceptorTagBySecurityRequirement[operationSecurityRequirement]
        if (authTag == null) {
            return null
        }
        return AnnotationSpec
            .builder(Classes.interceptWith.asKt())
            .addMember("value = %T::class", Classes.httpServerInterceptor.asKt())
            .addMember("tag = %T::class", ClassName(apiPackage, "ApiSecurity", authTag))
            .build();

    }
}
