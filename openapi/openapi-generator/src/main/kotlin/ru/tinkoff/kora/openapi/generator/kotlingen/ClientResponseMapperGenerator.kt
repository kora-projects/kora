package ru.tinkoff.kora.openapi.generator.kotlingen

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.openapitools.codegen.CodegenOperation
import org.openapitools.codegen.CodegenResponse
import org.openapitools.codegen.model.OperationsMap
import ru.tinkoff.kora.openapi.generator.KoraCodegen

class ClientResponseMapperGenerator : AbstractKotlinGenerator<OperationsMap>() {
    override fun generate(ctx: OperationsMap): FileSpec {
        val className = ClassName(apiPackage, ctx["classname"].toString() + "ClientResponseMappers")
        val b = TypeSpec.interfaceBuilder(className)
            .addAnnotation(generated())
        for (operation in ctx.operations.operation) {
            for (response in operation.responses) {
                b.addType(responseMapper(ctx, className, operation, response))
            }
        }

        return FileSpec.get(apiPackage, b.build())
    }

    private fun responseMapper(ctx: OperationsMap, mappers: ClassName, operation: CodegenOperation, response: CodegenResponse): TypeSpec {
        val responseType = ClassName(apiPackage, ctx["classname"].toString() + "Responses", capitalize(operation.operationId) + "ApiResponse")
        val className = mappers.nestedClass(capitalize(operation.operationId) + response.code + "ApiResponseMapper")
        val b = TypeSpec.classBuilder(className)
            .addAnnotation(generated())
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
