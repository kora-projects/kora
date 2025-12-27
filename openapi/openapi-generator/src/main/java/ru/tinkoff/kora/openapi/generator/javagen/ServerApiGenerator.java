package ru.tinkoff.kora.openapi.generator.javagen;

import com.palantir.javapoet.*;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.model.OperationsMap;

import javax.lang.model.element.Modifier;

public class ServerApiGenerator extends AbstractJavaGenerator<OperationsMap> {
    @Override
    public JavaFile generate(OperationsMap ctx) {
        var b = TypeSpec.classBuilder(ctx.get("classname") + "Controller")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(generated())
            .addAnnotation(Classes.component);
        if (params.prefixPath != null) {
            b.addAnnotation(AnnotationSpec.builder(Classes.httpController).addMember("value", params.prefixPath).build());
        } else {
            b.addAnnotation(AnnotationSpec.builder(Classes.httpController).build());
        }
        var allowAspects = params.enableValidation || !params.additionalContractAnnotations.isEmpty();
        if (!allowAspects) {
            b.addModifiers(Modifier.FINAL);
        }
        var delegate = ClassName.get(apiPackage, ctx.get("classname") + "Delegate");
        b.addField(delegate, "delegate", Modifier.PRIVATE, Modifier.FINAL);
        b.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(delegate, "delegate")
            .addStatement("this.delegate = delegate")
            .build()
        );
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
        this.buildAdditionalAnnotations(tag).forEach(b::addAnnotation);
        this.buildImplicitHeaders(operation).forEach(b::addAnnotation);
        b.addAnnotation(this.buildHttpRoute(operation));
        var auth = buildServerMethodAuth(operation);
        if (auth != null) {
            b.addAnnotation(auth);
        }
        if (params.enableValidation) {
            b.addAnnotation(AnnotationSpec.builder(Classes.interceptWith).addMember("value", "$T.class", Classes.validationHttpServerInterceptor).build());
            b.addAnnotation(AnnotationSpec.builder(Classes.validate).build());
        }
        this.buildInterceptors(tag, Classes.httpServerInterceptor).forEach(b::addAnnotation);
        b.addAnnotation(AnnotationSpec.builder(Classes.mapping)
            .addMember("value", "$T.class", ClassName.get(apiPackage, ctx.get("classname").toString() + "ServerResponseMappers", StringUtils.capitalize(operation.operationId) + "ApiResponseMapper"))
            .build());
        b.addCode("return this.delegate.$N(", operation.operationId);
        var hasParams = false;
        if (params.requestInDelegateParams) {
            hasParams = true;
            b.addCode("_serverRequest");
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
            if (hasParams) {
                b.addCode(", ");
            }
            b.addCode("$N", param.paramName);
            hasParams = true;
        }
        if (operation.getHasFormParams()) {
            var className = ClassName.get(
                apiPackage, ctx.get("classname") + "Controller", StringUtils.capitalize(operation.operationId) + "FormParam"
            );
            var mapper = ClassName.get(
                apiPackage, ctx.get("classname") + "ServerRequestMappers", StringUtils.capitalize(operation.operationId) + "FormParamRequestMapper"
            );
            var parameter = ParameterSpec.builder(className, "form")
                .addAnnotation(AnnotationSpec.builder(Classes.mapping)
                    .addMember("value", "$T.class", mapper)
                    .build()
                )
                .build();
            b.addParameter(parameter);
            if (hasParams) {
                b.addCode(", ");
            }
            b.addCode("$N", parameter.name());
        }
        b.addCode(");\n");
        b.returns(ClassName.get(apiPackage, ctx.get("classname").toString() + "Responses", StringUtils.capitalize(operation.operationId) + "ApiResponse"));
        return b.build();
    }

    @Nullable
    protected AnnotationSpec buildServerMethodAuth(CodegenOperation operation) {
        var securityRequirement = security.securityRequirementByOperation.get(operation.operationId);
        if (securityRequirement == null || securityRequirement.isEmpty()) {
            return null;
        }
        var operationSecurityRequirement = security.securityRequirementByOperation.get(operation.operationId);
        var authTag = security.interceptorTagBySecurityRequirement.get(operationSecurityRequirement);
        return AnnotationSpec
            .builder(Classes.interceptWith)
            .addMember("value", "$T.class", Classes.httpServerInterceptor)
            .addMember("tag", "$T.class", ClassName.get(apiPackage, "ApiSecurity", authTag))
            .build();
    }

}
