package io.koraframework.openapi.generator.javagen;

import com.palantir.javapoet.*;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenResponse;
import org.openapitools.codegen.model.OperationsMap;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
            var ranges = operation.responses.stream()
                .filter(io.koraframework.openapi.generator.AbstractGenerator::isRangeCode)
                .sorted(Comparator.comparingInt(r -> rangeCodeLowerBound(r.code)))
                .toList();
            if (!ranges.isEmpty()) {
                var defaultResponse = operation.responses.stream()
                    .filter(r -> r.isDefault)
                    .findFirst()
                    .orElse(null);
                b.addType(defaultCodeMapper(ctx, className, operation, ranges, defaultResponse));
            }
        }

        return JavaFile.builder(apiPackage, b.build()).build();
    }

    /**
     * Builds the aggregate mapper registered as {@code @ResponseCodeMapper(code = DEFAULT, ...)} whenever
     * an operation declares status-code ranges ({@code 4XX}, {@code 5XX}, ...). Exact codes are still
     * registered directly; every other code reaches this mapper, which dispatches on the actual status
     * to the matching per-range mapper, falling back to the OpenAPI {@code default} response mapper or,
     * if none was declared, throwing like the runtime does for unmatched codes.
     */
    private TypeSpec defaultCodeMapper(OperationsMap ctx, ClassName mappers, CodegenOperation operation, List<CodegenResponse> ranges, CodegenResponse defaultResponse) {
        var op = capitalize(operation.operationId);
        var responseType = ClassName.get(apiPackage, ctx.get("classname") + "Responses", op + "ApiResponse");
        var className = mappers.nestedClass(op + "DefaultCodeApiResponseMapper");
        var b = TypeSpec.classBuilder(className)
            .addAnnotation(generated())
            .addAnnotation(Classes.defaultComponent)
            .addAnnotation(Classes.component)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addSuperinterface(ParameterizedTypeName.get(Classes.httpClientResponseMapper, responseType));

        record Delegate(String field, ClassName type) {}
        var delegates = new ArrayList<Delegate>();
        for (var range : ranges) {
            delegates.add(new Delegate("mapper" + range.code, mappers.nestedClass(op + range.code + "ApiResponseMapper")));
        }
        if (defaultResponse != null) {
            delegates.add(new Delegate("mapperDefault", mappers.nestedClass(op + defaultResponse.code + "ApiResponseMapper")));
        }

        var constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
        for (var delegate : delegates) {
            b.addField(delegate.type(), delegate.field(), Modifier.PRIVATE, Modifier.FINAL);
            constructor.addParameter(delegate.type(), delegate.field());
            constructor.addStatement("this.$N = $N", delegate.field(), delegate.field());
        }
        b.addMethod(constructor.build());

        var apply = MethodSpec.methodBuilder("apply")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .returns(responseType)
            .addParameter(Classes.httpClientResponse, "response")
            .addException(IOException.class)
            .addStatement("var code = response.code()");
        for (var range : ranges) {
            apply.beginControlFlow("if (code >= $L && code < $L)", rangeCodeLowerBound(range.code), rangeCodeUpperBound(range.code))
                .addStatement("return this.$N.apply(response)", "mapper" + range.code)
                .endControlFlow();
        }
        if (defaultResponse != null) {
            apply.addStatement("return this.mapperDefault.apply(response)");
        } else {
            apply.addStatement("throw $T.fromResponse(response)", Classes.httpClientResponseException);
        }
        b.addMethod(apply.build());
        return b.build();
    }

    private TypeSpec responseMapper(OperationsMap ctx, ClassName mappers, CodegenOperation operation, CodegenResponse response) {
        var responseType = ClassName.get(apiPackage, ctx.get("classname") + "Responses", capitalize(operation.operationId) + "ApiResponse");
        var className = mappers.nestedClass(capitalize(operation.operationId) + response.code + "ApiResponseMapper");
        var b = TypeSpec.classBuilder(className)
            .addAnnotation(generated())
            .addAnnotation(Classes.defaultComponent)
            .addAnnotation(Classes.component)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addSuperinterface(ParameterizedTypeName.get(Classes.httpClientResponseMapper, responseType));
        MethodSpec.Builder constructor = null;
        if (response.dataType != null) {
            var mapperType = ParameterizedTypeName.get(Classes.httpClientResponseMapper, asType(response));
            b.addField(mapperType, "delegate", Modifier.PRIVATE, Modifier.FINAL);
            var mapperParam = ParameterSpec.builder(mapperType, "delegate");
            if (isContentJson(response.getContent()) && requiresJsonMapper(response)) {
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
        if (hasDynamicStatusCode(response)) {
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
}
