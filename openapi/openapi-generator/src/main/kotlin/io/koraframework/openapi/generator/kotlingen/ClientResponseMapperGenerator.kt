package io.koraframework.openapi.generator.kotlingen

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
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
}
