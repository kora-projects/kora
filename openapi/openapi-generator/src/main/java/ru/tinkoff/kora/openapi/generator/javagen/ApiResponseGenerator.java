package ru.tinkoff.kora.openapi.generator.javagen;

import com.palantir.javapoet.*;
import org.openapitools.codegen.CodegenResponse;
import org.openapitools.codegen.model.OperationsMap;

import javax.lang.model.element.Modifier;

public class ApiResponseGenerator extends AbstractJavaGenerator<OperationsMap> {
    @Override
    public JavaFile generate(OperationsMap ctx) {
        var className = ClassName.get(apiPackage, ctx.get("classname") + "Responses");
        var b = TypeSpec.interfaceBuilder(className)
            .addAnnotation(generated());
        for (var operation : ctx.getOperations().getOperation()) {
            var responseClassName = className.nestedClass(capitalize(operation.operationId) + "ApiResponse");
            if (operation.responses.size() == 1) {
                b.addType(response(ctx, responseClassName, operation.responses.getFirst()));
            } else {
                var t = TypeSpec.interfaceBuilder(responseClassName)
                    .addAnnotation(generated())
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.SEALED);
                for (var response : operation.responses) {
                    var codeResponseName = responseClassName.nestedClass(capitalize(operation.operationId) + (response.isDefault ? "Default" : response.code) + "ApiResponse");
                    t.addType(response(ctx, codeResponseName, response));
                }
                b.addType(t.build());
            }
        }

        return JavaFile.builder(apiPackage, b.build()).build();
    }

    private TypeSpec response(OperationsMap ctx, ClassName name, CodegenResponse response) {
        var t = TypeSpec.recordBuilder(name)
            .addAnnotation(generated())
            .addJavadoc("%s (status code %s)".formatted(response.message, response.code))
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC);
        if (name.enclosingClassName().enclosingClassName() != null) {
            t.addSuperinterface(name.enclosingClassName());
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

        return t.recordConstructor(c.build()).build();
    }

}
