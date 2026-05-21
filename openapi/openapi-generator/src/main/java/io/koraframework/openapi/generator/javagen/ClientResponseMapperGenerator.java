package io.koraframework.openapi.generator.javagen;

import com.palantir.javapoet.*;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenResponse;
import org.openapitools.codegen.model.OperationsMap;

import javax.lang.model.element.Modifier;
import java.io.IOException;

import static io.koraframework.openapi.generator.KoraCodegen.isContentJson;

public class ClientResponseMapperGenerator extends AbstractJavaGenerator<OperationsMap> {

    private static final ClassName HTTP_CLIENT_RESPONSE_EXCEPTION = ClassName.get(
        "io.koraframework.http.client.common.exception", "HttpClientResponseException");
    private static final ClassName HTTP_CLIENT_DECODED_RESPONSE_EXCEPTION = ClassName.get(
        "io.koraframework.http.client.common.exception", "HttpClientDecodedResponseException");

    @Override
    public JavaFile generate(OperationsMap ctx) {
        var className = ClassName.get(apiPackage, ctx.get("classname") + "ClientResponseMappers");
        var b = TypeSpec.interfaceBuilder(className)
            .addAnnotation(generated())
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC);
        for (var operation : ctx.getOperations().getOperation()) {
            if (Boolean.TRUE.equals(operation.vendorExtensions.get("plainResponse"))) {
                var plainDataType = operation.vendorExtensions.get("plainResponseDataType");
                if (plainDataType != null) {
                    b.addType(plainResponseMapper(ctx, className, operation));
                }
            } else {
                for (var response : operation.responses) {
                    b.addType(responseMapper(ctx, className, operation, response));
                }
            }
        }

        return JavaFile.builder(apiPackage, b.build()).build();
    }

    @SuppressWarnings("unchecked")
    private TypeSpec plainResponseMapper(OperationsMap ctx, ClassName mappers, CodegenOperation operation) {
        var successResponse = operation.responses.stream()
            .filter(r -> !r.isDefault && r.code != null && r.code.startsWith("2") && r.dataType != null)
            .findFirst()
            .orElseThrow();
        var successType = asType(successResponse);
        var className = mappers.nestedClass(capitalize(operation.operationId) + "PlainResponseMapper");
        var b = TypeSpec.classBuilder(className)
            .addAnnotation(generated())
            .addAnnotation(Classes.component)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .addSuperinterface(ParameterizedTypeName.get(Classes.httpClientResponseMapper, successType));

        var constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);

        // Success mapper field
        var successMapperType = ParameterizedTypeName.get(Classes.httpClientResponseMapper, successType);
        b.addField(successMapperType, "successMapper", Modifier.PRIVATE, Modifier.FINAL);
        var successParam = ParameterSpec.builder(successMapperType, "successMapper");
        if (isContentJson(successResponse.getContent())) {
            successParam.addAnnotation(jsonAnnotation());
        }
        constructor.addParameter(successParam.build()).addStatement("this.successMapper = successMapper");

        // Error mapper fields
        var errorResponses = (java.util.List<CodegenResponse>) operation.vendorExtensions.getOrDefault("plainErrorResponses", java.util.List.of());
        for (var error : errorResponses) {
            var errorType = asType(error);
            var errorMapperType = ParameterizedTypeName.get(Classes.httpClientResponseMapper, errorType);
            var fieldName = errorMapperFieldName(error);
            b.addField(errorMapperType, fieldName, Modifier.PRIVATE, Modifier.FINAL);
            var errorParam = ParameterSpec.builder(errorMapperType, fieldName);
            if (isContentJson(error.getContent())) {
                errorParam.addAnnotation(jsonAnnotation());
            }
            constructor.addParameter(errorParam.build()).addStatement("this.$N = $N", fieldName, fieldName);
        }

        // apply() method
        var apply = MethodSpec.methodBuilder("apply")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .returns(successType)
            .addParameter(Classes.httpClientResponse, "response")
            .addException(IOException.class);

        apply.addStatement("var code = response.code()");
        apply.beginControlFlow("if (code >= 200 && code < 300)")
            .addStatement("return successMapper.apply(response)")
            .endControlFlow();

        for (var error : errorResponses) {
            if (!error.isDefault) {
                var fieldName = errorMapperFieldName(error);
                apply.beginControlFlow("if (code == $L)", error.code)
                    .addStatement("var body = this.$N.apply(response)", fieldName)
                    .addStatement("throw new $T(code, response.headers(), body)", HTTP_CLIENT_DECODED_RESPONSE_EXCEPTION)
                    .endControlFlow();
            }
        }

        // Default error handler
        var hasDefaultError = errorResponses.stream().anyMatch(r -> r.isDefault);
        if (hasDefaultError) {
            var defaultError = errorResponses.stream().filter(r -> r.isDefault).findFirst().orElseThrow();
            var fieldName = errorMapperFieldName(defaultError);
            apply.addStatement("var body = this.$N.apply(response)", fieldName)
                .addStatement("throw new $T(code, response.headers(), body)", HTTP_CLIENT_DECODED_RESPONSE_EXCEPTION);
        } else {
            apply.addStatement("throw $T.fromResponse(response)", HTTP_CLIENT_RESPONSE_EXCEPTION);
        }

        return b.addMethod(constructor.build()).addMethod(apply.build()).build();
    }

    private static String errorMapperFieldName(CodegenResponse error) {
        return error.isDefault ? "errorDefaultMapper" : "error" + error.code + "Mapper";
    }

    private TypeSpec responseMapper(OperationsMap ctx, ClassName mappers, CodegenOperation operation, CodegenResponse response) {
        var responseType = ClassName.get(apiPackage, ctx.get("classname") + "Responses", capitalize(operation.operationId) + "ApiResponse");
        var className = mappers.nestedClass(capitalize(operation.operationId) + response.code + "ApiResponseMapper");
        var b = TypeSpec.classBuilder(className)
            .addAnnotation(generated())
            .addAnnotation(Classes.component)
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

        return b
            .addMethod(constructor.build())
            .addMethod(apply.addStatement("return new $T($L)", responseWithCodeType, newArgs.build()).build()).build();
    }
}
