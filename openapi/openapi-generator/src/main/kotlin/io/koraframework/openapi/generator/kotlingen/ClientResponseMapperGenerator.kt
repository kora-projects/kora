package io.koraframework.openapi.generator.kotlingen

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.openapitools.codegen.CodegenOperation
import org.openapitools.codegen.CodegenResponse
import org.openapitools.codegen.model.OperationsMap
import io.koraframework.openapi.generator.KoraCodegen

class ClientResponseMapperGenerator : AbstractKotlinGenerator<OperationsMap>() {

    companion object {
        private val HTTP_CLIENT_RESPONSE_EXCEPTION = com.palantir.javapoet.ClassName.get(
            "io.koraframework.http.client.common.exception", "HttpClientResponseException")
        private val HTTP_CLIENT_DECODED_RESPONSE_EXCEPTION = com.palantir.javapoet.ClassName.get(
            "io.koraframework.http.client.common.exception", "HttpClientDecodedResponseException")
    }

    private fun errorMapperFieldName(error: CodegenResponse): String {
        return if (error.isDefault) "errorDefaultMapper" else "error${error.code}Mapper"
    }

    override fun generate(ctx: OperationsMap): FileSpec {
        val className = ClassName(apiPackage, ctx["classname"].toString() + "ClientResponseMappers")
        val b = TypeSpec.interfaceBuilder(className)
            .addAnnotation(generated())
        for (operation in ctx.operations.operation) {
            if (operation.vendorExtensions["plainResponse"] == true) {
                val plainDataType = operation.vendorExtensions["plainResponseDataType"]
                if (plainDataType != null) {
                    b.addType(plainResponseMapper(ctx, className, operation))
                }
            } else {
                for (response in operation.responses) {
                    b.addType(responseMapper(ctx, className, operation, response))
                }
            }
        }

        return FileSpec.get(apiPackage, b.build())
    }

    @Suppress("UNCHECKED_CAST")
    private fun plainResponseMapper(ctx: OperationsMap, mappers: ClassName, operation: CodegenOperation): TypeSpec {
        val successResponse = operation.responses.first { r -> !r.isDefault && r.code != null && r.code.startsWith("2") && r.dataType != null }
        val successType = asType(successResponse).asKt()
        val className = mappers.nestedClass(capitalize(operation.operationId) + "PlainResponseMapper")
        val b = TypeSpec.classBuilder(className)
            .addAnnotation(generated())
            .addAnnotation(Classes.component.asKt())
            .addSuperinterface(Classes.httpClientResponseMapper.asKt().parameterizedBy(successType))

        val constructor = FunSpec.constructorBuilder()

        // Success mapper
        val successMapperType = Classes.httpClientResponseMapper.asKt().parameterizedBy(successType)
        b.addProperty(PropertySpec.builder("successMapper", successMapperType).initializer("successMapper").build())
        val successParam = ParameterSpec.builder("successMapper", successMapperType)
        if (KoraCodegen.isContentJson(successResponse.content)) {
            successParam.addAnnotation(jsonAnnotation())
        }
        constructor.addParameter(successParam.build())

        // Error mappers
        val errorResponses = operation.vendorExtensions.getOrDefault("plainErrorResponses", emptyList<CodegenResponse>()) as List<CodegenResponse>
        for (error in errorResponses) {
            val errorType = asType(error).asKt()
            val errorMapperType = Classes.httpClientResponseMapper.asKt().parameterizedBy(errorType)
            val fieldName = errorMapperFieldName(error)
            b.addProperty(PropertySpec.builder(fieldName, errorMapperType).initializer(fieldName).build())
            val errorParam = ParameterSpec.builder(fieldName, errorMapperType)
            if (KoraCodegen.isContentJson(error.content)) {
                errorParam.addAnnotation(jsonAnnotation())
            }
            constructor.addParameter(errorParam.build())
        }

        // apply()
        val apply = FunSpec.builder("apply")
            .addModifiers(KModifier.OVERRIDE)
            .returns(successType)
            .addParameter("response", Classes.httpClientResponse.asKt())

        apply.addStatement("val code = response.code()")
        apply.beginControlFlow("if (code in 200..299)")
        apply.addStatement("return successMapper.apply(response)!!")
        apply.endControlFlow()

        for (error in errorResponses) {
            if (!error.isDefault) {
                val fieldName = errorMapperFieldName(error)
                apply.beginControlFlow("if (code == %L)", error.code)
                apply.addStatement("val body = this.%N.apply(response)", fieldName)
                apply.addStatement("throw %T(code, response.headers(), body)", HTTP_CLIENT_DECODED_RESPONSE_EXCEPTION.asKt())
                apply.endControlFlow()
            }
        }

        val hasDefaultError = errorResponses.any { it.isDefault }
        if (hasDefaultError) {
            val defaultError = errorResponses.first { it.isDefault }
            val fieldName = errorMapperFieldName(defaultError)
            apply.addStatement("val body = this.%N.apply(response)", fieldName)
            apply.addStatement("throw %T(code, response.headers(), body)", HTTP_CLIENT_DECODED_RESPONSE_EXCEPTION.asKt())
        } else {
            apply.addStatement("throw %T.fromResponse(response)", HTTP_CLIENT_RESPONSE_EXCEPTION.asKt())
        }

        b.primaryConstructor(constructor.build())
        b.addFunction(apply.build())
        return b.build()
    }

    private fun responseMapper(ctx: OperationsMap, mappers: ClassName, operation: CodegenOperation, response: CodegenResponse): TypeSpec {
        val responseType = ClassName(apiPackage, ctx["classname"].toString() + "Responses", capitalize(operation.operationId) + "ApiResponse")
        val className = mappers.nestedClass(capitalize(operation.operationId) + response.code + "ApiResponseMapper")
        val b = TypeSpec.classBuilder(className)
            .addAnnotation(generated())
            .addAnnotation(Classes.component.asKt())
            .addSuperinterface(Classes.httpClientResponseMapper.asKt().parameterizedBy(responseType))
        val constructor = FunSpec.constructorBuilder()
        response.dataType?.let {
            val mapperType = Classes.httpClientResponseMapper.asKt().parameterizedBy(asType(response).asKt())
            b.addProperty(PropertySpec.builder("delegate", mapperType).initializer("delegate").build())
            val mapperParam = ParameterSpec.builder("delegate", mapperType)
            if (KoraCodegen.isContentJson(response.content)) {
                mapperParam.addAnnotation(jsonAnnotation())
            }
            constructor.addParameter(mapperParam.build())
        }
        val apply = FunSpec.builder("apply")
            .addModifiers(KModifier.OVERRIDE)
            .returns(responseType)
            .addParameter("response", Classes.httpClientResponse.asKt())

        for (header in response.headers) {
            apply.addStatement("val %N = response.headers().getFirst(%S)", header.name, header.baseName)
            if (header.required) {
                apply.beginControlFlow("if (%N == null)", header.name)
                apply.addStatement("throw %T(%S)", NullPointerException::class.asClassName(), "${header.baseName} is required but was null")
                apply.endControlFlow()
            }
        }
        if (response.dataType != null) {
            apply.addStatement("val content = this.delegate.apply(response)!!")
        }

        val responseWithCodeType = if (operation.responses.size == 1)
            responseType
        else
            responseType.nestedClass(capitalize(operation.operationId) + (if (response.isDefault) "Default" else response.code) + "ApiResponse")
        val newArgs = CodeBlock.builder()
        if (response.isDefault) {
            newArgs.add("response.code()")
        }
        if (response.dataType != null) {
            if (!newArgs.isEmpty()) {
                newArgs.add(", ")
            }
            newArgs.add("content")
        }
        for (header in response.headers) {
            if (!newArgs.isEmpty()) {
                newArgs.add(", ")
            }
            newArgs.add("%N", header.name)
        }
        apply.addStatement("return %T(%L)", responseWithCodeType, newArgs.build())

        b.primaryConstructor(constructor.build())
        b.addFunction(apply.build())
        return b.build()
    }
}
