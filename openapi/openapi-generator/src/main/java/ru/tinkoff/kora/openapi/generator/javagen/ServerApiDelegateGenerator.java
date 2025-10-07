package ru.tinkoff.kora.openapi.generator.javagen;

import com.palantir.javapoet.*;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.model.OperationsMap;
import ru.tinkoff.kora.openapi.generator.DelegateMethodBodyMode;

import javax.lang.model.element.Modifier;

public class ServerApiDelegateGenerator extends AbstractJavaGenerator<OperationsMap> {
    @Override
    public JavaFile generate(OperationsMap ctx) {
        var b = TypeSpec.interfaceBuilder(ctx.get("classname") + "Delegate")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(generated());

        for (var operation : ctx.getOperations().getOperation()) {
            b.addMethod(buildMethod(ctx, operation));
            if (operation.getHasFormParams()) {
                b.addType(buildFormParamsRecord(ctx, operation));
            }
        }


        return JavaFile.builder(apiPackage, b.build()).build();
    }

    private MethodSpec buildMethod(OperationsMap ctx, CodegenOperation operation) {
        var tag = ctx.get("baseName").toString();
        var b = MethodSpec.methodBuilder(operation.operationId)
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc(buildMethodJavadoc(ctx, operation))
            .addException(Exception.class);
        if (operation.isDeprecated) {
            b.addAnnotation(Deprecated.class);
        }
        if (params.delegateMethodBodyMode != DelegateMethodBodyMode.NONE) {
            b.addModifiers(Modifier.DEFAULT);
        } else {
            b.addModifiers(Modifier.ABSTRACT);
        }
        this.buildAdditionalAnnotations(tag).forEach(b::addAnnotation);
        this.buildImplicitHeaders(operation).forEach(b::addAnnotation);
        b.addAnnotation(this.buildHttpRoute(operation));
        if (params.delegateMethodBodyMode == DelegateMethodBodyMode.THROW_EXCEPTION) {
            b.addStatement("throw new UnsupportedOperationException($S)", "Not yet implemented");
        }
        if (params.requestInDelegateParams) {
            b.addParameter(Classes.httpServerRequest, "_serverRequest");
        }
        for (var param : operation.allParams) {
            if (param.isFormParam) {
                continue; // form params are handled separately
            }
            if (param.isHeaderParam && operation.implicitHeadersParams != null && operation.implicitHeadersParams.stream().anyMatch(h -> h.paramName.equals(param.paramName))) {
                continue;
            }
            b.addParameter(this.buildParameter(ctx, operation, param));
        }
        if (operation.getHasFormParams()) {
            var className = ClassName.get(
                apiPackage, ctx.get("classname") + "Controller", capitalize(operation.operationId) + "FormParam"
            );
            var mapper = ClassName.get(
                apiPackage, ctx.get("classname") + "ServerRequestMappers", capitalize(operation.operationId) + "FormParamRequestMapper"
            );
            var parameter = ParameterSpec.builder(className, "form")
                .addAnnotation(AnnotationSpec.builder(Classes.mapping)
                    .addMember("value", "$T.class", mapper)
                    .build()
                )
                .build();
            b.addParameter(parameter);
        }
        b.returns(ClassName.get(apiPackage, ctx.get("classname").toString() + "Responses", capitalize(operation.operationId) + "ApiResponse"));
        return b.build();
    }
}
