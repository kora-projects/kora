package io.koraframework.openapi.generator.kotlingen

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.koraframework.openapi.generator.CodegenParams.ClientResponseMode.SUCCESSFUL
import org.openapitools.codegen.CodegenOperation
import org.openapitools.codegen.CodegenResponse
import org.openapitools.codegen.model.OperationsMap
import io.koraframework.openapi.generator.KoraCodegen

class ClientResponseMapperGenerator : AbstractKotlinGenerator<OperationsMap>() {
    override fun generate(ctx: OperationsMap): FileSpec {
        val className = ClassName(apiPackage, ctx["classname"].toString() + "ClientResponseMappers")
        val b = TypeSpec.interfaceBuilder(className)
            .addAnnotation(generated())
        for (operation in ctx.operations.operation) {
            for (response in operation.responses) {
                b.addType(responseMapper(ctx, className, operation, response))
            }
            if (usesSuccessfulResponseMapper(ctx, operation)) {
                b.addType(operationResponseMapper(ctx, className, operation))
            }
        }

        return FileSpec.get(apiPackage, b.build())
    }

    private fun responseMapper(ctx: OperationsMap, mappers: ClassName, operation: CodegenOperation, response: CodegenResponse): TypeSpec {
        val responseType = ClassName(apiPackage, ctx["classname"].toString() + "Responses", capitalize(operation.operationId) + "ApiResponse")
        val className = mappers.nestedClass(capitalize(operation.operationId) + response.code + "ApiResponseMapper")
        val b = TypeSpec.classBuilder(className)
            .addAnnotation(generated())
            .addAnnotation(Classes.defaultComponent.asKt())
            .addModifiers(KModifier.OPEN)
            .addSuperinterface(Classes.httpClientResponseMapper.asKt().parameterizedBy(responseType))
        val constructor = FunSpec.constructorBuilder()
        response.dataType?.let {
            val mapperType = Classes.httpClientResponseMapper.asKt().parameterizedBy(asType(response).asKt())
            b.addProperty(PropertySpec.builder("delegate", mapperType).initializer("delegate").build())
            val mapperParam = ParameterSpec.builder("delegate", mapperType)
            if (KoraCodegen.isContentJson(response.content) && requiresJsonMapper(response)) {
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

        val constructorSpec = constructor.build()
        if (constructorSpec.parameters.isNotEmpty()) {
            b.primaryConstructor(constructorSpec)
        }
        b.addFunction(apply.build())
        return b.build()
    }

    private fun operationResponseMapper(ctx: OperationsMap, mappers: ClassName, operation: CodegenOperation): TypeSpec {
        val returnType = clientReturnType(ctx, operation)
        val className = mappers.nestedClass(capitalize(operation.operationId) + "SuccessfulResponseMapper")
        val exceptionType = ClassName(apiPackage, ctx["classname"].toString(), ClientApiGenerator.responseExceptionSimpleName(ctx, operation))
        val b = TypeSpec.classBuilder(className)
            .addAnnotation(generated())
            .addAnnotation(Classes.defaultComponent.asKt())
            .addModifiers(KModifier.OPEN)
            .addSuperinterface(Classes.httpClientResponseMapper.asKt().parameterizedBy(returnType))
        val constructor = FunSpec.constructorBuilder()
        for (response in operation.responses) {
            val mapperType = responseMapperClassName(mappers, operation, response)
            val fieldName = responseMapperFieldName(operation, response)
            constructor.addParameter(fieldName, mapperType)
            b.addProperty(PropertySpec.builder(fieldName, mapperType).initializer(fieldName).build())
        }
        b.primaryConstructor(constructor.build())
        val apply = FunSpec.builder("apply")
            .addModifiers(KModifier.OVERRIDE)
            .returns(returnType)
            .addParameter("response", Classes.httpClientResponse.asKt())
            .addStatement("val _code = response.code()")
            .beginControlFlow("return when (_code)")
        for (response in operation.responses) {
            if (response.isDefault) {
                continue
            }
            val code = response.code.toInt()
            if (code >= 200 && code < 300) {
                apply.addStatement("%L -> this.%N.apply(response) as %T", code, responseMapperFieldName(operation, response), returnType)
            } else {
                apply.beginControlFlow("%L ->", code)
                addErrorResponseMapping(apply, fullResponseType(ctx, operation), exceptionType, responseMapperFieldName(operation, response))
                apply.endControlFlow()
            }
        }
        val defaultResponse = operation.responses.firstOrNull { it.isDefault }
        if (defaultResponse != null) {
            apply.beginControlFlow("else ->")
            addErrorResponseMapping(apply, fullResponseType(ctx, operation), exceptionType, responseMapperFieldName(operation, defaultResponse))
            apply.endControlFlow()
        } else {
            apply.addStatement("else -> throw %T.fromResponse(response)", Classes.httpClientResponseException.asKt())
        }
        apply.endControlFlow()
        b.addType(bufferedResponseType())
        b.addFunction(bufferedResponseFunction())
        b.addFunction(responseExceptionFunction())
        b.addFunction(apply.build())
        return b.build()
    }

    private fun addErrorResponseMapping(apply: FunSpec.Builder, responseType: ClassName, exceptionType: ClassName, mapperFieldName: String) {
        apply.addStatement("val _bufferedResponse = bufferedResponse(response)")
            .addCode("val _response: %T = try {\n", responseType)
            .addCode("  this.%N.apply(_bufferedResponse.response)\n", mapperFieldName)
            .addCode("} catch (e: Exception) {\n")
            .addCode("  throw responseException(response, _bufferedResponse.body, e)\n")
            .addCode("}\n")
            .addStatement("throw %T(response.code(), response.headers(), _response, _bufferedResponse.body)", exceptionType)
    }

    private fun bufferedResponseType(): TypeSpec {
        return TypeSpec.classBuilder("BufferedResponse")
            .addModifiers(KModifier.PRIVATE, KModifier.DATA)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("body", BYTE_ARRAY)
                    .addParameter("response", Classes.httpClientResponse.asKt())
                    .build()
            )
            .addProperty(PropertySpec.builder("body", BYTE_ARRAY).initializer("body").build())
            .addProperty(PropertySpec.builder("response", Classes.httpClientResponse.asKt()).initializer("response").build())
            .build()
    }

    private fun bufferedResponseFunction(): FunSpec {
        return FunSpec.builder("bufferedResponse")
            .addModifiers(KModifier.PRIVATE)
            .returns(ClassName("", "BufferedResponse"))
            .addParameter("response", Classes.httpClientResponse.asKt())
            .addCode("response.body().use { body ->\n")
            .addCode("  val contentType = body.contentType()\n")
            .addCode("  val full = body.getFullContentIfAvailable()\n")
            .beginControlFlow("if (full != null)")
            .addStatement("val bytes = ByteArray(full.remaining())")
            .addStatement("full.get(bytes)")
            .addStatement("return BufferedResponse(bytes, %T(response.code(), response.headers(), %T.of(contentType, bytes)))", Classes.simpleHttpClientResponse.asKt(), Classes.httpBody.asKt())
            .endControlFlow()
            .addCode("  val bytes = body.asInputStream().use { it.readAllBytes() }\n")
            .addCode("  return BufferedResponse(bytes, %T(response.code(), response.headers(), %T.of(contentType, bytes)))\n", Classes.simpleHttpClientResponse.asKt(), Classes.httpBody.asKt())
            .addCode("}\n")
            .build()
    }

    private fun responseExceptionFunction(): FunSpec {
        return FunSpec.builder("responseException")
            .addModifiers(KModifier.PRIVATE)
            .returns(Classes.httpClientResponseException.asKt())
            .addParameter("response", Classes.httpClientResponse.asKt())
            .addParameter("body", BYTE_ARRAY)
            .addParameter("cause", Exception::class)
            .addStatement("val exception = %T(response.code(), response.headers(), body)", Classes.httpClientResponseException.asKt())
            .addStatement("exception.addSuppressed(cause)")
            .addStatement("return exception")
            .build()
    }

    private fun responseMapperClassName(mappers: ClassName, operation: CodegenOperation, response: CodegenResponse): ClassName {
        return mappers.nestedClass(capitalize(operation.operationId) + response.code + "ApiResponseMapper")
    }

    private fun responseMapperFieldName(operation: CodegenOperation, response: CodegenResponse): String {
        return operation.operationId + capitalize(if (response.isDefault) "Default" else response.code) + "ResponseMapper"
    }

    private fun usesSuccessfulResponseMapper(ctx: OperationsMap, operation: CodegenOperation): Boolean {
        return params.clientResponseMode == SUCCESSFUL && hasErrorResponses(operation)
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

    private fun clientReturnType(ctx: OperationsMap, operation: CodegenOperation): TypeName {
        val responseClassName = fullResponseType(ctx, operation)
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
                responseClassName.nestedClass(capitalize(operation.operationId) + response.code + "ApiResponse")
        }
        if (successfulResponses.size > 1) {
            val dataType = successfulResponses.first().dataType
            if (dataType != null && successfulResponses.all { dataType == it.dataType }) {
                return responseClassName.nestedClass(responseClassName.simpleName.removeSuffix("ApiResponse") + sanitizeSharedResponseName(dataType) + "ApiResponse")
            }
        }
        return responseClassName
    }

    private fun fullResponseType(ctx: OperationsMap, operation: CodegenOperation): ClassName {
        return ClassName(apiPackage, ctx["classname"].toString() + "Responses", capitalize(operation.operationId) + "ApiResponse")
    }

    private fun sanitizeSharedResponseName(dataType: String): String {
        val name = dataType.replace(Regex("[^a-zA-Z0-9]"), "")
        return if (name.isBlank()) "Content" else capitalize(name)
    }
}
