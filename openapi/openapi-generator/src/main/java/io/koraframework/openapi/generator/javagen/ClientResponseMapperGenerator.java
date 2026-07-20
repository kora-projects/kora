package io.koraframework.openapi.generator.javagen;

import com.palantir.javapoet.*;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenResponse;
import org.openapitools.codegen.model.OperationsMap;

import javax.lang.model.element.Modifier;
import java.io.IOException;

import static io.koraframework.openapi.generator.CodegenParams.ClientResponseMode.SUCCESSFUL;
import static io.koraframework.openapi.generator.KoraCodegen.isContentJson;

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
            if (usesSuccessfulResponseMapper(ctx, operation)) {
                b.addType(operationResponseMapper(ctx, className, operation));
            }
        }

        return JavaFile.builder(apiPackage, b.build()).build();
    }

    private TypeSpec responseMapper(OperationsMap ctx, ClassName mappers, CodegenOperation operation, CodegenResponse response) {
        var responseType = ClassName.get(apiPackage, ctx.get("classname") + "Responses", capitalize(operation.operationId) + "ApiResponse");
        var className = mappers.nestedClass(capitalize(operation.operationId) + response.code + "ApiResponseMapper");
        var b = TypeSpec.classBuilder(className)
            .addAnnotation(generated())
            .addAnnotation(Classes.defaultComponent)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addSuperinterface(ParameterizedTypeName.get(Classes.httpClientResponseMapper, responseType));
        MethodSpec.Builder constructor = null;
        if (response.dataType != null) {
            var mapperType = ParameterizedTypeName.get(Classes.httpClientResponseMapper, asType(response));
            b.addField(mapperType, "delegate", Modifier.PRIVATE, Modifier.FINAL);
            var mapperParam = ParameterSpec.builder(mapperType, "delegate");
            if (isContentJson(response.getContent()) && !isBareObject(response)) {
                mapperParam.addAnnotation(jsonAnnotation());
            }
            constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(mapperParam.build())
                .addStatement("this.delegate = delegate");
        }

        var apply = MethodSpec.methodBuilder("apply")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .returns(responseType)
            .addParameter(Classes.httpClientResponse, "response")
            .addException(IOException.class);

        for (var header : response.headers) {
            apply.addStatement("var $N = response.headers().getFirst($S)", header.name, header.baseName);
            if (header.required) {
                apply.beginControlFlow("if ($N == null)", header.name)
                    .addStatement("throw new $T($S)", NullPointerException.class, "%s header is required, but was null".formatted(header.baseName))
                    .endControlFlow();
            }
        }
        if (response.dataType != null) {
            apply.addStatement("var content = this.delegate.apply(response)");
        }


        var responseWithCodeType = operation.responses.size() == 1
            ? responseType
            : responseType.nestedClass(capitalize(operation.operationId) + (response.isDefault ? "Default" : response.code) + "ApiResponse");
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
            newArgs.add(header.name);
        }

        if (constructor != null) {
            b.addMethod(constructor.build());
        }

        return b.addMethod(apply.addStatement("return new $T($L)", responseWithCodeType, newArgs.build()).build()).build();
    }

    private TypeSpec operationResponseMapper(OperationsMap ctx, ClassName mappers, CodegenOperation operation) {
        var returnType = clientReturnType(ctx, operation);
        var className = mappers.nestedClass(capitalize(operation.operationId) + "SuccessfulResponseMapper");
        var exceptionType = ClassName.get(apiPackage, ctx.get("classname").toString(), ClientApiGenerator.responseExceptionSimpleName(ctx, operation));
        var b = TypeSpec.classBuilder(className)
            .addAnnotation(generated())
            .addAnnotation(Classes.defaultComponent)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addSuperinterface(ParameterizedTypeName.get(Classes.httpClientResponseMapper, returnType));
        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);
        for (var response : operation.responses) {
            var mapperType = responseMapperClassName(ctx, mappers, operation, response);
            var fieldName = responseMapperFieldName(operation, response);
            b.addField(mapperType, fieldName, Modifier.PRIVATE, Modifier.FINAL);
            constructor.addParameter(mapperType, fieldName);
            constructor.addStatement("this.$N = $N", fieldName, fieldName);
        }
        b.addMethod(constructor.build());

        var apply = MethodSpec.methodBuilder("apply")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(returnType)
            .addParameter(Classes.httpClientResponse, "response")
            .addException(IOException.class)
            .addStatement("var _code = response.code()");
        apply.beginControlFlow("switch (_code)");
        for (var response : operation.responses) {
            if (response.isDefault) {
                continue;
            }
            var code = Integer.parseInt(response.code);
            apply.beginControlFlow("case $L ->", code);
            if (code >= 200 && code < 300) {
                apply.addStatement("return ($T) this.$N.apply(response)", returnType, responseMapperFieldName(operation, response));
            } else {
                apply.addStatement("throw new $T(response.code(), response.headers(), this.$N.apply(response))", exceptionType, responseMapperFieldName(operation, response));
            }
            apply.endControlFlow();
        }
        apply.beginControlFlow("default ->");
        var defaultResponse = operation.responses.stream().filter(response -> response.isDefault).findFirst();
        if (defaultResponse.isPresent()) {
            apply.addStatement("throw new $T(response.code(), response.headers(), this.$N.apply(response))", exceptionType, responseMapperFieldName(operation, defaultResponse.get()));
        } else {
            apply.addStatement("throw $T.fromResponse(response)", Classes.httpClientResponseException);
        }
        apply.endControlFlow();
        apply.endControlFlow();
        return b.addMethod(apply.build()).build();
    }

    private ClassName responseMapperClassName(OperationsMap ctx, ClassName mappers, CodegenOperation operation, CodegenResponse response) {
        return mappers.nestedClass(capitalize(operation.operationId) + response.code + "ApiResponseMapper");
    }

    private String responseMapperFieldName(CodegenOperation operation, CodegenResponse response) {
        return operation.operationId + capitalize(response.isDefault ? "Default" : response.code) + "ResponseMapper";
    }

    private boolean usesSuccessfulResponseMapper(OperationsMap ctx, CodegenOperation operation) {
        if (params.clientResponseMode != SUCCESSFUL || !hasErrorResponses(operation)) {
            return false;
        }
        return !clientReturnType(ctx, operation).equals(fullResponseType(ctx, operation));
    }

    private boolean hasErrorResponses(CodegenOperation operation) {
        return operation.responses.stream().anyMatch(response -> {
            if (response.isDefault) {
                return true;
            }
            var code = Integer.parseInt(response.code);
            return code < 200 || code >= 300;
        });
    }

    private TypeName clientReturnType(OperationsMap ctx, CodegenOperation operation) {
        var responseClassName = fullResponseType(ctx, operation);
        if (params.clientResponseMode != SUCCESSFUL) {
            return responseClassName;
        }
        var successfulResponses = operation.responses.stream()
            .filter(response -> !response.isDefault)
            .filter(response -> {
                var code = Integer.parseInt(response.code);
                return code >= 200 && code < 300;
            })
            .toList();
        if (successfulResponses.size() == 1) {
            var response = successfulResponses.getFirst();
            return operation.responses.size() == 1
                ? responseClassName
                : responseClassName.nestedClass(capitalize(operation.operationId) + response.code + "ApiResponse");
        }
        if (successfulResponses.size() > 1) {
            var dataType = successfulResponses.getFirst().dataType;
            if (dataType != null && successfulResponses.stream().allMatch(response -> dataType.equals(response.dataType))) {
                return responseClassName.nestedClass(responseClassName.simpleName().replaceAll("ApiResponse$", "") + sanitizeSharedResponseName(dataType) + "ApiResponse");
            }
        }
        return responseClassName;
    }

    private ClassName fullResponseType(OperationsMap ctx, CodegenOperation operation) {
        return ClassName.get(apiPackage, ctx.get("classname") + "Responses", capitalize(operation.operationId) + "ApiResponse");
    }

    private static String sanitizeSharedResponseName(String dataType) {
        var name = dataType.replaceAll("[^a-zA-Z0-9]", "");
        return name.isBlank() ? "Content" : capitalize(name);
    }
}
