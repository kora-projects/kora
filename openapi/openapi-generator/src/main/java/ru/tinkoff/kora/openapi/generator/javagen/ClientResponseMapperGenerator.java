package ru.tinkoff.kora.openapi.generator.javagen;

import com.palantir.javapoet.*;
import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenResponse;
import org.openapitools.codegen.model.OperationsMap;

import javax.lang.model.element.Modifier;
import java.io.IOException;

import static ru.tinkoff.kora.openapi.generator.KoraCodegen.isContentJson;

public class ClientResponseMapperGenerator extends AbstractJavaGenerator<OperationsMap> {
    @Override
    public JavaFile generate(OperationsMap ctx) {
        var className = ClassName.get(apiPackage, ctx.get("classname") + "ClientResponseMappers");
        var b = TypeSpec.interfaceBuilder(className)
            .addAnnotation(generated())
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC);
        for (var operation : ctx.getOperations().getOperation()) {
            for (var response : operation.responses) {
                b.addType(responseMapper(ctx, className, operation, response));
            }
        }

        return JavaFile.builder(apiPackage, b.build()).build();
    }

    private TypeSpec responseMapper(OperationsMap ctx, ClassName mappers, CodegenOperation operation, CodegenResponse response) {
        var responseType = ClassName.get(apiPackage, ctx.get("classname") + "Responses", StringUtils.capitalize(operation.operationId) + "ApiResponse");
        var className = mappers.nestedClass(StringUtils.capitalize(operation.operationId) + response.code + "ApiResponseMapper");
        var b = TypeSpec.classBuilder(className)
            .addAnnotation(generated())
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .addSuperinterface(ParameterizedTypeName.get(Classes.httpClientResponseMapper, responseType));
        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);
        if (response.dataType != null) {
            var mapperType = ParameterizedTypeName.get(Classes.httpClientResponseMapper, asType(response));
            b.addField(mapperType, "delegate", Modifier.PRIVATE, Modifier.FINAL);
            var mapperParam = ParameterSpec.builder(mapperType, "delegate");
            if (isContentJson(response.getContent())) {
                mapperParam.addAnnotation(jsonAnnotation());
            }
            constructor.addParameter(mapperParam.build())
                .addStatement("this.delegate = delegate");
        }

        var apply = MethodSpec.methodBuilder("apply")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .returns(responseType)
            .addParameter(Classes.httpClientResponse, "response")
            .addException(IOException.class);

        for (var header : response.headers) {
            apply.addStatement("var $N = response.headers().getFirst($S)", header.nameInCamelCase, header.baseName);
            if (header.required) {
                apply.beginControlFlow("if ($N == null)", header.nameInCamelCase)
                    .addStatement("throw new $T($S)", NullPointerException.class, "%s header is required, but was null".formatted(header.baseName))
                    .endControlFlow();
            }
        }
        if (response.dataType != null) {
            apply.addStatement("var content = this.delegate.apply(response)");
        }


        var responseWithCodeType = operation.responses.size() == 1
            ? responseType
            : responseType.nestedClass(StringUtils.capitalize(operation.operationId) + (response.isDefault ? "Default" : response.code) + "ApiResponse");
        var newArgs = CodeBlock.builder();
        if (response.isDefault) {
            newArgs.add("response.code()");
        }
        if (response.dataType != null) {
            if (!newArgs.isEmpty()) {
                newArgs.add(", ");
            }
            newArgs.add("content");
        }
        for (var header : response.headers) {
            if (!newArgs.isEmpty()) {
                newArgs.add(", ");
            }
            newArgs.add(header.nameInCamelCase);
        }

        return b
            .addMethod(constructor.build())
            .addMethod(apply.addStatement("return new $T($L)", responseWithCodeType, newArgs.build()).build()).build();
    }
}
