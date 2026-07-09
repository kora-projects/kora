package io.koraframework.openapi.generator.javagen;

import com.palantir.javapoet.*;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenResponse;
import org.openapitools.codegen.model.OperationsMap;

import javax.lang.model.element.Modifier;
import java.util.*;
import java.util.stream.Collectors;

public class ApiResponseGenerator extends AbstractJavaGenerator<OperationsMap> {
    @Override
    public JavaFile generate(OperationsMap ctx) {
        var className = ClassName.get(apiPackage, ctx.get("classname") + "Responses");
        var b = TypeSpec.interfaceBuilder(className)
            .addAnnotation(generated())
            .addModifiers(Modifier.PUBLIC);
        for (var operation : ctx.getOperations().getOperation()) {
            var responseClassName = className.nestedClass(capitalize(operation.operationId) + "ApiResponse");
            if (operation.responses.size() == 1) {
                b.addType(response(ctx, responseClassName, operation.responses.getFirst(), null));
            } else {
                var t = TypeSpec.interfaceBuilder(responseClassName)
                    .addAnnotation(generated())
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.SEALED);
                var sharedResponses = sharedResponses(ctx, responseClassName, operation.responses);
                for (var sharedResponse : sharedResponses.values()) {
                    t.addType(sharedResponse.type);
                }
                for (var response : operation.responses) {
                    var codeResponseName = responseClassName.nestedClass(capitalize(operation.operationId) + (response.isDefault ? "Default" : response.code) + "ApiResponse");
                    t.addType(response(ctx, codeResponseName, response, sharedResponses.get(response.dataType)));
                }
                b.addType(t.build());
            }
        }

        return JavaFile.builder(apiPackage, b.build()).build();
    }

    private TypeSpec response(OperationsMap ctx, ClassName name, CodegenResponse response, SharedResponse sharedResponse) {
        var t = TypeSpec.recordBuilder(name)
            .addAnnotation(generated())
            .addJavadoc("%s (status code %s)".formatted(response.message, response.code))
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC);
        if (name.enclosingClassName().enclosingClassName() != null) {
            t.addSuperinterface(sharedResponse == null ? name.enclosingClassName() : sharedResponse.className);
        }
        var c = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);
        if (response.isDefault) {
            c.addParameter(TypeName.INT, "statusCode");
        }
        if (response.dataType != null) {
            c.addParameter(asType(response), "content");
        }
        for (var header : response.headers) {
            var type = asType(header); // todo Some header decoding maybe? We can do it
            c.addParameter(ClassName.get(String.class), header.nameInCamelCase);
        }

        if (sharedResponse != null && sharedResponse.statusCodeMethod != null && (!response.isDefault || !"statusCode".equals(sharedResponse.statusCodeMethod))) {
            var method = MethodSpec.methodBuilder(sharedResponse.statusCodeMethod)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.INT);
            if (response.isDefault) {
                method.addStatement("return this.statusCode");
            } else {
                method.addStatement("return $L", response.code);
            }
            t.addMethod(method.build());
        }
        return t.recordConstructor(c.build()).build();
    }

    private Map<String, SharedResponse> sharedResponses(OperationsMap ctx, ClassName responseClassName, List<CodegenResponse> responses) {
        var responseByDataType = responses.stream()
            .filter(response -> response.dataType != null)
            .collect(Collectors.groupingBy(response -> response.dataType, LinkedHashMap::new, Collectors.toList()));
        var result = new HashMap<String, SharedResponse>();
        for (var entry : responseByDataType.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue;
            }
            var dataType = entry.getKey();
            var contentType = asType(entry.getValue().getFirst());
            var model = model(dataType);
            var occupiedNames = model == null
                ? Set.<String>of()
                : model.vars.stream().map(property -> property.name).collect(Collectors.toSet());
            var statusCodeMethod = statusCodeMethod(occupiedNames);
            var className = responseClassName.nestedClass(responseClassName.simpleName().replaceAll("ApiResponse$", "") + sanitizeSharedResponseName(dataType) + "ApiResponse");
            var type = TypeSpec.interfaceBuilder(className)
                .addAnnotation(generated())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.NON_SEALED)
                .addSuperinterface(responseClassName)
                .addMethod(MethodSpec.methodBuilder("content")
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .returns(contentType)
                    .build());
            if (model != null) {
                for (var property : model.vars) {
                    if ("content".equals(property.name) || Objects.equals(property.name, statusCodeMethod)) {
                        continue;
                    }
                    type.addMethod(MethodSpec.methodBuilder(property.name)
                        .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                        .returns(asType(property))
                        .addStatement("return this.content().$N()", property.name)
                        .build());
                }
            }
            if (statusCodeMethod != null) {
                type.addMethod(MethodSpec.methodBuilder(statusCodeMethod)
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .returns(TypeName.INT)
                    .build());
            }
            result.put(dataType, new SharedResponse(className, type.build(), statusCodeMethod));
        }
        return result;
    }

    private CodegenModel model(String dataType) {
        var model = models.get(dataType);
        if (model != null && !model.getModels().isEmpty()) {
            return model.getModels().getFirst().getModel();
        }
        for (var modelMap : models.values()) {
            if (modelMap.getModels().isEmpty()) {
                continue;
            }
            var codegenModel = modelMap.getModels().getFirst().getModel();
            if (dataType.equals(codegenModel.classname)) {
                return codegenModel;
            }
        }
        return null;
    }

    private static String sanitizeSharedResponseName(String dataType) {
        var name = dataType.replaceAll("[^a-zA-Z0-9]", "");
        if (name.isBlank()) {
            return "Content";
        }
        return capitalize(name);
    }

    private static String statusCodeMethod(Set<String> occupiedNames) {
        var candidates = List.of("statusCode", "httpStatusCode", "statusCodeMethod", "httpStatusCodeMethod");
        for (var candidate : candidates) {
            if (!occupiedNames.contains(candidate)) {
                return candidate;
            }
        }
        for (var candidate : candidates) {
            var underscored = "_" + candidate;
            if (!occupiedNames.contains(underscored)) {
                return underscored;
            }
        }
        return null;
    }

    private record SharedResponse(ClassName className, TypeSpec type, String statusCodeMethod) {}
}
