package ru.tinkoff.kora.openapi.generator.kotlingen

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.openapitools.codegen.CodegenOperation
import org.openapitools.codegen.CodegenResponse
import org.openapitools.codegen.model.OperationsMap
import ru.tinkoff.kora.openapi.generator.KoraCodegen


class ServerResponseMappersGenerator : AbstractKotlinGenerator<OperationsMap>() {
    override fun generate(ctx: OperationsMap): FileSpec {
        val b = TypeSpec.interfaceBuilder(ctx.get("classname").toString() + "ServerResponseMappers")
            .addAnnotation(generated())
            .addAnnotation(Classes.module.asKt())

        for (operation in ctx.operations.operation) {
            b.addType(buildMapper(ctx, operation))
        }

        return FileSpec.get(apiPackage, b.build())
    }

    private fun buildMapper(ctx: OperationsMap, operation: CodegenOperation): TypeSpec {
        val className = ClassName(ctx.get("classname").toString() + "ServerResponseMappers", capitalize(operation.operationId) + "ApiResponseMapper");
        val responseClassName = ClassName(apiPackage, ctx.get("classname").toString() + "Responses", capitalize(operation.operationId) + "ApiResponse");
        val b = TypeSpec.classBuilder(className)
            .addAnnotation(generated())
            .addSuperinterface(Classes.httpServerResponseMapper.asKt().parameterizedBy(responseClassName));

        val constructor = FunSpec.constructorBuilder()
        for (response in operation.responses) {
            if (!response.isBinary && response.dataType != null) {
                val mapperType = Classes.httpServerResponseMapper.asKt().parameterizedBy(Classes.httpResponseEntity.asKt().parameterizedBy(asType(response).asKt()))
                val mapperName = "response" + response.code + "Delegate"
                b.addProperty(PropertySpec.builder(mapperName, mapperType).initializer(mapperName).build())
                val param = ParameterSpec.builder(mapperName, mapperType)
                if (KoraCodegen.isContentJson(response.content)) {
                    param.addAnnotation(Classes.json.asKt())
                }
                constructor.addParameter(param.build())
            }
        }
        b.primaryConstructor(constructor.build())
        val m = FunSpec.builder("apply")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("ctx", Classes.context.asKt())
            .addParameter("request", Classes.httpServerRequest.asKt())
            .addParameter("rs", responseClassName)
            .returns(Classes.httpServerResponse.asKt())
        if (operation.responses.size == 1) {
            m.addCode(buildMapResponse(ctx, operation, operation.responses.single()))
        } else {
            m.beginControlFlow("when (rs)")
            for (response in operation.responses) {
                m.beginControlFlow("is %T -> ", responseClassName.nestedClass(capitalize(operation.operationId) + (if (response.isDefault) "Default" else response.code) + "ApiResponse"))
                m.addCode(buildMapResponse(ctx, operation, response))
                m.endControlFlow()
            }
            m.endControlFlow()
        }
        b.addFunction(m.build())
        return b.build()
    }

    private fun buildMapResponse(ctx: OperationsMap, operation: CodegenOperation, rs: CodegenResponse): CodeBlock {
        val b = CodeBlock.builder()
        b.addStatement("val headers = %T.of()", Classes.httpHeaders.asKt())
        for (header in rs.headers) {
            if (header.required) {
                b.addStatement("headers.set(%S, rs.%N)", header.baseName, header.name)
            } else {
                b.beginControlFlow("if (rs.%N != null)", header.name)
                    .addStatement("headers.set(%S, rs.%N)", header.baseName, header.name)
                    .endControlFlow()
            }
        }
        val responseCode = if (rs.isDefault)
            CodeBlock.of("rs.statusCode")
        else
            CodeBlock.of(rs.code)
        if (rs.isBinary) {
            val contentType = rs.content.sequencedKeySet().getFirst()
            b.addStatement("return %T.of(%L, headers, %T.of(%S, rs.content))", Classes.httpServerResponse.asKt(), responseCode, Classes.httpBody.asKt(), contentType)
            return b.build()
        }
        if (rs.dataType == null) {
            b.addStatement("return %T.of(%L, headers)", Classes.httpServerResponse.asKt(), responseCode)
            return b.build()
        }
        b.addStatement("val entity = %T.of(%L, headers, rs.content)", Classes.httpResponseEntity.asKt(), responseCode)
        b.addStatement("return this.%N.apply(ctx, request, entity)", "response" + rs.code + "Delegate")
        return b.build()
    }
}
