package ru.tinkoff.kora.openapi.generator.javagen;

import com.palantir.javapoet.*;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenResponse;
import org.openapitools.codegen.model.OperationsMap;
import ru.tinkoff.kora.openapi.generator.KoraCodegen;

import javax.lang.model.element.Modifier;
import java.io.IOException;

public class ServerResponseMapperGenerator extends AbstractJavaGenerator<OperationsMap> {
    @Override
    public JavaFile generate(OperationsMap ctx) {
        var b = TypeSpec.interfaceBuilder(ctx.get("classname") + "ServerResponseMappers")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(generated())
            .addAnnotation(Classes.module);

        for (var operation : ctx.getOperations().getOperation()) {
            b.addType(buildMapper(ctx, operation));
        }

        return JavaFile.builder(apiPackage, b.build()).build();
    }

    private TypeSpec buildMapper(OperationsMap ctx, CodegenOperation operation) {
        var className = ClassName.get(ctx.get("classname") + "ServerResponseMappers", capitalize(operation.operationId) + "ApiResponseMapper");
        var responseClassName = ClassName.get(apiPackage, ctx.get("classname") + "Responses", capitalize(operation.operationId) + "ApiResponse");
        var b = TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .addAnnotation(generated())
            .addSuperinterface(ParameterizedTypeName.get(Classes.httpServerResponseMapper, responseClassName));
        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);
        for (var response : operation.responses) {
            if (!response.isBinary && response.dataType != null) {
                var mapperType = ParameterizedTypeName.get(Classes.httpServerResponseMapper, ParameterizedTypeName.get(Classes.httpResponseEntity, asType(response)));
                var mapperName = "response" + response.code + "Delegate";
                b.addField(mapperType, mapperName, Modifier.PRIVATE, Modifier.FINAL);
                var param = ParameterSpec.builder(mapperType, mapperName);
                if (KoraCodegen.isContentJson(response.getContent())) {
                    param.addAnnotation(Classes.json);
                }
                constructor.addParameter(param.build());
                constructor.addStatement("this.$N = $N", mapperName, mapperName);
            }
        }
        b.addMethod(constructor.build());
        var m = MethodSpec.methodBuilder("apply")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .addParameter(Classes.httpServerRequest, "request")
            .addParameter(responseClassName, "response")
            .returns(Classes.httpServerResponse)
            .addException(IOException.class);
        if (operation.responses.size() == 1) {
            m.addCode(buildMapResponse(ctx, operation, operation.responses.getFirst(), "response"));
        } else {
            m.beginControlFlow("switch (response)");
            for (var response : operation.responses) {
                m.beginControlFlow("case $T rs -> ", responseClassName.nestedClass(capitalize(operation.operationId) + (response.isDefault ? "Default" : response.code) + "ApiResponse"));
                m.addCode(buildMapResponse(ctx, operation, response, "rs"));
                m.endControlFlow();
            }
            m.endControlFlow();
        }
        b.addMethod(m.build());
        return b.build();
    }

    private CodeBlock buildMapResponse(OperationsMap ctx, CodegenOperation operation, CodegenResponse rs, String rsName) {
        var b = CodeBlock.builder();
        b.addStatement("var headers = $T.of()", Classes.httpHeaders);
        for (var header : rs.headers) {
            if (header.required) {
                b.addStatement("headers.set($S, $N.$N())", header.baseName, rsName, header.name);
            } else {
                b.beginControlFlow("if ($N.$N() != null)", rsName, header.name)
                    .addStatement("headers.set($S, $N.$N())", header.baseName, rsName, header.name)
                    .endControlFlow();
            }
        }
        var responseCode = rs.isDefault
            ? CodeBlock.of("$N.statusCode()", rsName)
            : CodeBlock.of(rs.code);
        if (rs.isBinary) {
            var contentType = rs.getContent().sequencedKeySet().getFirst();
            b.addStatement("return $T.of($L, headers, $T.of($S, $N.content()))", Classes.httpServerResponse, responseCode, Classes.httpBody, contentType, rsName);
            return b.build();
        }
        if (rs.dataType == null) {
            b.addStatement("return $T.of($L, headers)", Classes.httpServerResponse, responseCode);
            return b.build();
        }
        b.addStatement("var entity = $T.of($L, headers, $N.content())", Classes.httpResponseEntity, responseCode, rsName);
        b.addStatement("return this.$N.apply(request, entity)", "response" + rs.code + "Delegate");
        return b.build();
    }
}
