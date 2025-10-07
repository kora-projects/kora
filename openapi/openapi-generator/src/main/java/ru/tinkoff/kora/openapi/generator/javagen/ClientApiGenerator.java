package ru.tinkoff.kora.openapi.generator.javagen;

import com.palantir.javapoet.*;
import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenSecurity;
import org.openapitools.codegen.model.OperationsMap;

import javax.lang.model.element.Modifier;
import java.util.List;

public class ClientApiGenerator extends AbstractJavaGenerator<OperationsMap> {

    @Override
    public JavaFile generate(OperationsMap ctx) {
        var b = TypeSpec.interfaceBuilder((String) ctx.get("classname"))
            .addAnnotation(generated())
            .addAnnotation(buildHttpClientAnnotation(ctx));
        for (var operation : ctx.getOperations().getOperation()) {
            b.addMethod(buildMethod(ctx, operation));
            if (operation.getHasFormParams()) {
                b.addType(buildFormParamsRecord(ctx, operation));
            }
            var optionalParams = operation.optionalParams.stream()
                .filter(p -> !p.isPathParam)
                .filter(p -> !p.isFormParam)
                .filter(p -> !(p.isHeaderParam && operation.implicitHeadersParams.stream().anyMatch(h -> p.paramName.equals(h.paramName))))
                .toList();
            if (!optionalParams.isEmpty()) {
                b.addType(buildJavaClientApiOptionalParams(ctx, operation, optionalParams));
                b.addMethod(buildRequiredArgsCall(ctx, operation, optionalParams));
                b.addMethod(buildRequiredArgsWithArgsCall(ctx, operation, optionalParams));
            }
        }

        return JavaFile.builder(apiPackage, b.build()).build();
    }

    private MethodSpec buildRequiredArgsCall(OperationsMap ctx, CodegenOperation operation, List<CodegenParameter> optionalParams) {
        var returnType = ClassName.get(apiPackage, ctx.get("classname") + "Responses", StringUtils.capitalize(operation.operationId) + "ApiResponse");
        var b = MethodSpec.methodBuilder(operation.operationId)
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .returns(returnType)
            .addCode("return this.$N(", operation.operationId);
        if (operation.isDeprecated) {
            b.addAnnotation(Deprecated.class);
        }
        var paramsCounter = 0;
        if (operation.hasAuthMethods && params.authAsMethodArgument) {
            for (var param : this.buildAuthParameters(operation)) {
                b.addParameter(param);
                if (paramsCounter > 0) {
                    b.addCode(", ");
                }
                paramsCounter++;
                b.addCode("$N", param.name());
            }
        }

        for (int i = 0; i < operation.allParams.size(); i++) {
            var p = operation.allParams.get(i);
            if (p.isFormParam) {
                continue;
            }
            if (p.isHeaderParam && operation.implicitHeadersParams.stream().anyMatch(h -> p.paramName.equals(h.paramName))) {
                continue;
            }
            if (paramsCounter > 0) {
                b.addCode(", ");
            }
            if (p.isPathParam) {
                var type = asType(ctx, operation, p);
                b.addParameter(type, p.paramName);
                b.addCode(p.paramName);
                paramsCounter++;
                continue;
            }
            var type = asType(ctx, operation, p);
            if (p.required) {
                b.addParameter(type, p.paramName);
                b.addCode(p.paramName);
            } else {
                b.addCode("($T) null", type.box());
            }
            paramsCounter++;
        }
        return b.addCode(");\n").build();
    }

    private MethodSpec buildRequiredArgsWithArgsCall(OperationsMap ctx, CodegenOperation operation, List<CodegenParameter> optionalParams) {
        var returnType = ClassName.get(apiPackage, ctx.get("classname") + "Responses", StringUtils.capitalize(operation.operationId) + "ApiResponse");
        var b = MethodSpec.methodBuilder(operation.operationId)
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .returns(returnType)
            .addCode("return this.$N(", operation.operationId);
        if (operation.isDeprecated) {
            b.addAnnotation(Deprecated.class);
        }
        var paramsCounter = 0;
        if (operation.hasAuthMethods && params.authAsMethodArgument) {
            for (var param : this.buildAuthParameters(operation)) {
                b.addParameter(param);
                if (paramsCounter > 0) {
                    b.addCode(", ");
                }
                paramsCounter++;
                b.addCode("$N", param.name());
            }
        }
        for (int i = 0; i < operation.allParams.size(); i++) {
            var p = operation.allParams.get(i);
            if (p.isFormParam) {
                continue;
            }
            if (p.isHeaderParam && operation.implicitHeadersParams.stream().anyMatch(h -> p.paramName.equals(h.paramName))) {
                continue;
            }
            if (paramsCounter > 0) {
                b.addCode(", ");
            }
            if (p.isPathParam) {
                var type = asType(ctx, operation, p);
                b.addParameter(type, p.paramName);
                b.addCode(p.paramName);
                paramsCounter++;
                continue;
            }
            if (optionalParams.stream().anyMatch(o -> p.paramName.equals(o.paramName))) {
                b.addCode("optionalArguments.$N()", p.paramName);
            } else {
                var type = asType(ctx, operation, p);
                b.addParameter(type, p.paramName);
                b.addCode(p.paramName);
            }
            paramsCounter++;
        }
        b.addParameter(ClassName.get(apiPackage, ctx.get("classname").toString(), StringUtils.capitalize(operation.operationId) + "OptArgs"), "optionalArguments");
        return b.addCode(");\n").build();
    }

    private TypeSpec buildJavaClientApiOptionalParams(OperationsMap ctx, CodegenOperation operation, List<CodegenParameter> optionalParams) {
        var b = MethodSpec.constructorBuilder();
        if (operation.isDeprecated) {
            b.addAnnotation(Deprecated.class);
        }
        for (var optionalParam : optionalParams) {
            var type = asType(ctx, operation, optionalParam).box();
            b.addParameter(ParameterSpec.builder(type, optionalParam.paramName)
                .addAnnotation(Classes.nullable)
                .build()
            );
        }
        var recordClassName = ClassName.get(apiPackage, ctx.get("classname").toString(), StringUtils.capitalize(operation.operationId) + "OptArgs");

        var typeSpec = TypeSpec.recordBuilder(recordClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .recordConstructor(b.build());
        var empty = MethodSpec.methodBuilder("empty")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(recordClassName)
            .addCode("return new $T(", recordClassName);
        for (int i = 0; i < optionalParams.size(); i++) {
            if (i > 0) {
                empty.addCode(", ");
            }
            empty.addCode("null");
        }
        empty.addCode(");\n");
        typeSpec.addMethod(empty.build());

        var defaults = MethodSpec.methodBuilder("defaults")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(recordClassName)
            .addCode("return new $T(", recordClassName);
        for (int i = 0; i < optionalParams.size(); i++) {
            if (i > 0) {
                defaults.addCode(", ");
            }
            var p = optionalParams.get(i);
            if (p.defaultValue != null) {
                defaults.addCode(p.defaultValue);
            } else if (p.enumDefaultValue != null) {
                defaults.addCode(p.enumDefaultValue);
            } else {
                defaults.addCode("null");
            }
        }
        defaults.addCode(");\n");
        typeSpec.addMethod(defaults.build());


        for (var optionalParam : optionalParams) {
            var type = asType(ctx, operation, optionalParam).box();
            var wither = MethodSpec.methodBuilder("with" + StringUtils.capitalize(optionalParam.paramName))
                .returns(recordClassName)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(type, optionalParam.paramName)
                .addCode("return new $T(", recordClassName);
            for (int i = 0; i < optionalParams.size(); i++) {
                if (i > 0) {
                    wither.addCode(", ");
                }
                var p = optionalParams.get(i);
                wither.addCode(p.paramName);
            }
            wither.addCode(");\n");
            typeSpec.addMethod(wither.build());
        }

        return typeSpec.build();
    }


    private MethodSpec buildMethod(OperationsMap ctx, CodegenOperation operation) {
        var tag = ctx.get("baseName").toString();
        var b = MethodSpec.methodBuilder(operation.operationId)
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addJavadoc(buildMethodJavadoc(ctx, operation));
        if (operation.isDeprecated) {
            b.addAnnotation(Deprecated.class);
        }
        this.buildAdditionalAnnotations(tag).forEach(b::addAnnotation);
        this.buildImplicitHeaders(operation).forEach(b::addAnnotation);
        b.addAnnotation(this.buildHttpRoute(operation));
        for (var response : operation.responses) {
            b.addAnnotation(AnnotationSpec.builder(Classes.responseCodeMapper)
                .addMember("code", "$L", response.isDefault ? "-1" : response.code)
                .addMember("mapper", "$T.class", ClassName.get(apiPackage, ctx.get("classname") + "ClientResponseMappers", StringUtils.capitalize(operation.operationId) + response.code + "ApiResponseMapper"))
                .build()
            );
        }
        if (!params.authAsMethodArgument) {
            var requirement = this.security.securityRequirementByOperation.get(operation.operationId);
            if (requirement != null && !requirement.isEmpty()) {
                var interceptorTag = this.security.interceptorTagBySecurityRequirement.get(requirement);
                var annotation = AnnotationSpec.builder(Classes.interceptWith)
                    .addMember("value", "$T.class", Classes.httpClientInterceptor)
                    .addMember("tag", "@$T($T.class)", Classes.tag, ClassName.get(apiPackage, "ApiSecurity", interceptorTag))
                    .build();
                b.addAnnotation(annotation);
            }
        }

        this.buildInterceptors(tag, Classes.httpClientInterceptor).forEach(b::addAnnotation);
        b.returns(ClassName.get(apiPackage, ctx.get("classname") + "Responses", StringUtils.capitalize(operation.operationId) + "ApiResponse"));
        if (operation.hasAuthMethods && params.authAsMethodArgument) {
            for (var param : this.buildAuthParameters(operation)) {
                b.addParameter(param);
            }
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
                apiPackage, (String) ctx.get("classname"), StringUtils.capitalize(operation.operationId) + "FormParam"
            );
            var mapper = ClassName.get(
                apiPackage, ctx.get("classname") + "ClientRequestMappers", StringUtils.capitalize(operation.operationId) + "FormParamRequestMapper"
            );
            var parameter = ParameterSpec.builder(className, "form")
                .addAnnotation(AnnotationSpec.builder(Classes.mapping)
                    .addMember("value", "$T.class", mapper)
                    .build()
                )
                .build();
            b.addParameter(parameter);
        }
        return b.build();
    }

    protected List<ParameterSpec> buildAuthParameters(CodegenOperation op) {
        return op.authMethods.stream()
            .filter(a -> params.primaryAuth == null || a.name.equals(params.primaryAuth))
            .map(authMethod -> buildAuthParameter(authMethod, op))
            .toList();
    }

    protected ParameterSpec buildAuthParameter(CodegenSecurity authMethod, CodegenOperation op) {
        var authName = getAuthName(authMethod.name, op.allParams);
        var p = ParameterSpec.builder(String.class, authName)
            .addAnnotation(Classes.nullable);
        if (authMethod.isKeyInQuery) {
            return p.addAnnotation(AnnotationSpec.builder(Classes.query)
                    .addMember("value", "$S", authMethod.keyParamName)
                    .build()
                )
                .build();
        }
        if (authMethod.isKeyInHeader) {
            return p.addAnnotation(AnnotationSpec.builder(Classes.header)
                    .addMember("value", "$S", authMethod.keyParamName)
                    .build()
                )
                .build();
        }
        if (authMethod.isKeyInCookie) {
            return p.addAnnotation(AnnotationSpec.builder(Classes.cookie)
                    .addMember("value", "$S", authMethod.keyParamName)
                    .build()
                )
                .build();
        }
        if (authMethod.isOAuth || authMethod.isOpenId || authMethod.isBasicBearer || authMethod.isBasic || authMethod.isBasicBasic) {
            for (var parameter : op.headerParams) {
                if ("Authorization".equalsIgnoreCase(parameter.paramName)) {
                    throw new IllegalArgumentException("Authorization argument as method parameter can't be set, cause parameter named 'Authorization' already is present");
                }
            }
            return p.addAnnotation(AnnotationSpec.builder(Classes.header)
                    .addMember("value", "$S", "Authorization")
                    .build()
                )
                .build();
        }

        throw new IllegalStateException("Auth argument can be in Query, Header or Cookie, but was unknown");
    }

    private static String getAuthName(String name, List<CodegenParameter> parameters) {
        for (var parameter : parameters) {
            if (name.equals(parameter.paramName)) {
                return getAuthName("_" + name, parameters);
            }
        }

        return name;
    }


    private AnnotationSpec buildHttpClientAnnotation(OperationsMap ctx) {
        var httpClientAnnotation = AnnotationSpec.builder(Classes.httpClient);
        if (params.clientConfigPrefix != null) {
            httpClientAnnotation.addMember("configPath", "$S", params.clientConfigPrefix + "." + ctx.get("classname"));
        }
        var tag = ctx.get("baseName").toString();
        var clientTag = params.clientTags.get(tag);
        var defaultTag = params.clientTags.get("*");
        if (clientTag != null && clientTag.httpClientTag() != null) {
            httpClientAnnotation.addMember("httpClientTag", clientTag.httpClientTag() + ".class");
        } else if (defaultTag != null && defaultTag.httpClientTag() != null) {
            httpClientAnnotation.addMember("httpClientTag", defaultTag.httpClientTag() + ".class");
        }
        if (clientTag != null && clientTag.telemetryTag() != null) {
            httpClientAnnotation.addMember("telemetryTag", clientTag.telemetryTag() + ".class");
        } else if (defaultTag != null && defaultTag.httpClientTag() != null) {
            httpClientAnnotation.addMember("telemetryTag", defaultTag.telemetryTag() + ".class");
        }
        return httpClientAnnotation.build();
    }
}
