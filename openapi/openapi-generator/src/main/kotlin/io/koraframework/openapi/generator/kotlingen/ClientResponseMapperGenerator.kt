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
            val ranges = operation.responses
                .filter { isRangeCode(it) }
                .sortedBy { rangeCodeLowerBound(it.code) }
            if (ranges.isNotEmpty()) {
                val defaultResponse = operation.responses.firstOrNull { it.isDefault }
                b.addType(defaultCodeMapper(ctx, className, operation, ranges, defaultResponse))
            }
            if (usesSuccessfulResponseMapper(ctx, operation)) {
                b.addType(operationResponseMapper(ctx, className, operation))
            }
        }

        return FileSpec.get(apiPackage, b.build())
    }

    /**
     * Builds the aggregate mapper registered as `@ResponseCodeMapper(code = DEFAULT, ...)` whenever an
     * operation declares status-code ranges (`4XX`, `5XX`, ...). Exact codes are still registered
     * directly; every other code reaches this mapper, which dispatches on the actual status to the
     * matching per-range mapper, falling back to the OpenAPI `default` response mapper or, if none was
     * declared, throwing like the runtime does for unmatched codes.
     */
    private fun defaultCodeMapper(ctx: OperationsMap, mappers: ClassName, operation: CodegenOperation, ranges: List<CodegenResponse>, defaultResponse: CodegenResponse?): TypeSpec {
        val op = capitalize(operation.operationId)
        val responseType = ClassName(apiPackage, ctx["classname"].toString() + "Responses", op + "ApiResponse")
        val className = mappers.nestedClass(op + "DefaultCodeApiResponseMapper")
        val b = TypeSpec.classBuilder(className)
            .addAnnotation(generated())
            .addAnnotation(Classes.defaultComponent.asKt())
            .addAnnotation(Classes.component.asKt())
            .addModifiers(KModifier.OPEN)
            .addSuperinterface(Classes.httpClientResponseMapper.asKt().parameterizedBy(responseType))

        data class Delegate(val field: String, val type: ClassName)
        val delegates = mutableListOf<Delegate>()
        for (range in ranges) {
            delegates.add(Delegate("mapper" + range.code, mappers.nestedClass(op + range.code + "ApiResponseMapper")))
        }
        if (defaultResponse != null) {
            delegates.add(Delegate("mapperDefault", mappers.nestedClass(op + defaultResponse.code + "ApiResponseMapper")))
        }

        val constructor = FunSpec.constructorBuilder()
        for (delegate in delegates) {
            constructor.addParameter(delegate.field, delegate.type)
            b.addProperty(PropertySpec.builder(delegate.field, delegate.type).initializer(delegate.field).addModifiers(KModifier.PRIVATE).build())
        }
        b.primaryConstructor(constructor.build())

        val apply = FunSpec.builder("apply")
            .addModifiers(KModifier.OVERRIDE)
            .returns(responseType)
            .addParameter("response", Classes.httpClientResponse.asKt())
            .addStatement("val code = response.code()")
        for (range in ranges) {
            apply.beginControlFlow("if (code >= %L && code < %L)", rangeCodeLowerBound(range.code), rangeCodeUpperBound(range.code))
            apply.addStatement("return this.%N.apply(response)", "mapper" + range.code)
            apply.endControlFlow()
        }
        if (defaultResponse != null) {
            apply.addStatement("return this.mapperDefault.apply(response)")
        } else {
            apply.addStatement("throw %T.fromResponse(response)", Classes.httpClientResponseException.asKt())
        }
        b.addFunction(apply.build())
        return b.build()
    }

    private fun responseMapper(ctx: OperationsMap, mappers: ClassName, operation: CodegenOperation, response: CodegenResponse): TypeSpec {
        val responseType = ClassName(apiPackage, ctx["classname"].toString() + "Responses", capitalize(operation.operationId) + "ApiResponse")
        val className = mappers.nestedClass(capitalize(operation.operationId) + response.code + "ApiResponseMapper")
        val b = TypeSpec.classBuilder(className)
            .addAnnotation(generated())
            .addAnnotation(Classes.defaultComponent.asKt())
            .addAnnotation(Classes.component.asKt())
            .addModifiers(KModifier.OPEN)
            .addSuperinterface(Classes.httpClientResponseMapper.asKt().parameterizedBy(responseType))
        val constructor = FunSpec.constructorBuilder()
        response.dataType?.let {
            val mapperType = Classes.httpClientResponseMapper.asKt().parameterizedBy(asType(response).asKt())
            b.addProperty(PropertySpec.builder("delegate", mapperType).initializer("delegate").build())
            val mapperParam = ParameterSpec.builder("delegate", mapperType)
            if (KoraCodegen.isContentJson(response.content) && requiresJsonMapper(response)) {
                mapperParam.addAnnotation(jsonAnnotation(AnnotationSpec.UseSiteTarget.PARAM))
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
        if (hasDynamicStatusCode(response)) {
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
        val b = TypeSpec.classBuilder(className)
            .addAnnotation(generated())
            .addAnnotation(Classes.defaultComponent.asKt())
            .addAnnotation(Classes.component.asKt())
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
        val exactCodes = operation.responses.filter { !hasDynamicStatusCode(it) }.map { it.code to it }
        val rangeCodes = operation.responses.filter { isRangeCode(it) }
            .sortedBy { rangeCodeLowerBound(it.code) }
            .map { "in ${rangeCodeLowerBound(it.code)} until ${rangeCodeUpperBound(it.code)}" to it }
        for ((condition, response) in exactCodes + rangeCodes) {
            if (isSuccessCode(response)) {
                apply.addStatement("%L -> this.%N.apply(response) as %T", condition, responseMapperFieldName(operation, response), returnType)
            } else {
                apply.beginControlFlow("%L ->", condition)
                addErrorResponseMapping(ctx, apply, operation, response)
                apply.endControlFlow()
            }
        }
        val defaultResponse = operation.responses.firstOrNull { it.isDefault }
        if (defaultResponse != null) {
            if (operation.responses.none { isSuccessCode(it) }) {
                // without a declared 2xx, the `default` response is the successful one too
                apply.addStatement("in 200 until 300 -> this.%N.apply(response)", responseMapperFieldName(operation, defaultResponse))
            }
            apply.beginControlFlow("else ->")
            addErrorResponseMapping(ctx, apply, operation, defaultResponse)
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

    private fun addErrorResponseMapping(ctx: OperationsMap, apply: FunSpec.Builder, operation: CodegenOperation, response: CodegenResponse) {
        val responseType = fullResponseType(ctx, operation)
        val responseWithCodeType = responseWithCodeType(ctx, operation, response)
        val exceptionType = ClassName(apiPackage, ctx["classname"].toString(), ClientApiGenerator.responseExceptionSimpleName(ctx, response))
        apply.addStatement("val _bufferedResponse = bufferedResponse(response)")
            .beginControlFlow("val _response: %T = try", responseType)
            .addStatement("this.%N.apply(_bufferedResponse.response)", responseMapperFieldName(operation, response))
            .nextControlFlow("catch (e: Exception)")
            .addStatement("throw responseException(response, _bufferedResponse.body, e)")
            .endControlFlow()
        if (response.dataType != null) {
            apply.addStatement("throw %T(response.code(), response.headers(), (_response as %T).content, _bufferedResponse.body)", exceptionType, responseWithCodeType)
        } else {
            apply.addStatement("throw %T(response.code(), response.headers(), _bufferedResponse.body)", exceptionType)
        }
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
            .beginControlFlow("response.body().use { body ->")
            .addStatement("val contentType = body.contentType()")
            .addStatement("val full = body.getFullContentIfAvailable()")
            .beginControlFlow("if (full != null)")
            .beginControlFlow("val bytes = if (full.hasArray() && full.arrayOffset() == 0 && full.array().size == full.remaining())")
            .addStatement("full.array()")
            .nextControlFlow("else")
            .addStatement("ByteArray(full.remaining()).also { full.get(it) }")
            .endControlFlow()
            .addStatement("return BufferedResponse(bytes, %T(response.code(), response.headers(), %T.of(contentType, bytes)))", Classes.simpleHttpClientResponse.asKt(), Classes.httpBody.asKt())
            .endControlFlow()
            .addStatement("val bytes = body.asInputStream().use { it.readAllBytes() }")
            .addStatement("return BufferedResponse(bytes, %T(response.code(), response.headers(), %T.of(contentType, bytes)))", Classes.simpleHttpClientResponse.asKt(), Classes.httpBody.asKt())
            .endControlFlow()
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
        return params.clientResponseMode == SUCCESSFUL && (hasErrorResponses(operation) || clientReturnType(ctx, operation) != fullResponseType(ctx, operation))
    }

    private fun hasErrorResponses(operation: CodegenOperation): Boolean {
        return operation.responses.any { !isSuccessCode(it) }
    }

    private fun clientReturnType(ctx: OperationsMap, operation: CodegenOperation): TypeName {
        val responseClassName = fullResponseType(ctx, operation)
        if (params.clientResponseMode != SUCCESSFUL) {
            return responseClassName
        }
        val successfulResponses = operation.responses
            .filter { isSuccessCode(it) }
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

    private fun responseWithCodeType(ctx: OperationsMap, operation: CodegenOperation, response: CodegenResponse): ClassName {
        val responseType = fullResponseType(ctx, operation)
        return if (operation.responses.size == 1)
            responseType
        else
            responseType.nestedClass(capitalize(operation.operationId) + (if (response.isDefault) "Default" else response.code) + "ApiResponse")
    }

    private fun sanitizeSharedResponseName(dataType: String): String {
        val name = dataType.replace(Regex("[^a-zA-Z0-9]"), "")
        return if (name.isBlank()) "Content" else capitalize(name)
    }
}
