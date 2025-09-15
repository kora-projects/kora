package ru.tinkoff.kora.openapi.generator.javagen;

import com.palantir.javapoet.*;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.model.OperationsMap;
import ru.tinkoff.kora.openapi.generator.KoraCodegen;

import javax.lang.model.element.Modifier;
import java.util.List;
import java.util.Objects;

import static org.openapitools.codegen.utils.StringUtils.camelize;

public class ClientApiGenerator extends AbstractJavaGenerator<OperationsMap> {

    @Override
    public JavaFile generate(OperationsMap ctx) {
        var b = TypeSpec.interfaceBuilder((String) ctx.get("classname"))
            .addAnnotation(AnnotationSpec.builder(Classes.generated).addMember("value", "$S", ClientApiGenerator.class.getCanonicalName()).build())
            .addAnnotation(buildHttpClientAnnotation(ctx));
        for (var operation : ctx.getOperations().getOperation()) {
            b.addMethod(buildMethod(ctx, operation));
            if (operation.getHasFormParams()) {
                b.addType(buildFormParamsRecord(ctx, operation));
            }
            // todo optional signatures
//     {{#vendorExtensions.x-have-optional}}
//     {{^hasFormParams}}
//
//     default {{classname}}Responses.{{#lambda.titlecase}}{{operationId}}{{/lambda.titlecase}}ApiResponse {{operationId}}({{#vendorExtensions.x-required-params}}
//         {{{dataType}}} {{paramName}}{{^-last}}, {{/-last}}{{/vendorExtensions.x-required-params}}) {
//         return {{operationId}}({{#allParams}}{{#required}}{{paramName}}{{/required}}{{^required}}{{#defaultValue}}{{#isEnum}}{{{enumDefaultValue}}}{{/isEnum}}{{^isEnum}}{{{defaultValue}}}{{/isEnum}}{{/defaultValue}}{{^defaultValue}}({{{dataType}}}) null{{/defaultValue}}{{/required}}{{^-last}}, {{/-last}}{{/allParams}});
//     }
//
//     default {{classname}}Responses.{{#lambda.titlecase}}{{operationId}}{{/lambda.titlecase}}ApiResponse {{operationId}}({{#vendorExtensions.x-required-params}}
//         {{{dataType}}} {{paramName}},{{/vendorExtensions.x-required-params}}
//         {{#lambda.titlecase}}{{operationId}}{{/lambda.titlecase}}OptArgs optionalArguments) {
//         return {{operationId}}({{#allParams}}{{#required}}{{paramName}}{{/required}}{{^required}}optionalArguments.{{paramName}}(){{/required}}{{^-last}}, {{/-last}}{{/allParams}});
//     }
//
//          {{>javaClientApiOptionalParams}}
//     {{/hasFormParams}}
//     {{/vendorExtensions.x-have-optional}}
        }

        return JavaFile.builder(apiPackage, b.build()).build();
    }

    private TypeSpec buildFormParamsRecord(OperationsMap ctx, CodegenOperation operation) {
//   /**
//    * {{#formParams}}{{#description}}
//    * @param {{paramName}} {{description}}{{#required}} (required){{/required}}{{^required}} (optional{{#defaultValue}}, default to {{.}}{{/defaultValue}}){{/required}}{{/description}}{{^description}}{{#defaultValue}}
//    * @param {{paramName}} {{description}}{{#required}} (required){{/required}}{{^required}} (optional{{#defaultValue}}, default to {{.}}{{/defaultValue}}){{/required}}{{/defaultValue}}{{/description}}{{/formParams}}
//    */
//    @ru.tinkoff.kora.common.annotation.Generated("openapi generator kora")
//    public record {{#lambda.titlecase}}{{operationId}}{{/lambda.titlecase}}FormParam({{#formParams}}
//        {{^required}}@Nullable {{/required}}{{#isFile}}ru.tinkoff.kora.http.common.form.FormMultipart.FormPart {{paramName}}{{^-last}},{{/-last}}{{/isFile}}{{^isFile}}{{{dataType}}} {{paramName}}{{^-last}},{{/-last}}{{/isFile}}{{/formParams}}
//    ) {}
        var b = MethodSpec.constructorBuilder();
        for (var formParam : operation.formParams) {
            var type = formParam.isFile
                ? Classes.formPart
                : asType(ctx, operation, formParam);
            if (!formParam.required) {
                type = type.box();
            }
            var p = ParameterSpec.builder(type, formParam.paramName);
            if (!formParam.required) {
                p.addAnnotation(Classes.nullable);
            }
            if (formParam.description != null) {
                p.addJavadoc(formParam.description).addJavadoc(" ");
            }
            if (formParam.required) {
                p.addJavadoc("(required)");
            } else if (formParam.defaultValue != null) {
                p.addJavadoc("(optional, default to " + formParam.defaultValue + ")");
            } else {
                p.addJavadoc("(optional)");
            }

            b.addParameter(p.build());
        }

        return TypeSpec.recordBuilder(StringUtils.capitalize(operation.operationId) + "FormParam")
            .addAnnotation(AnnotationSpec.builder(Classes.generated).addMember("value", "$S", ClientApiGenerator.class.getCanonicalName()).build())
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .recordConstructor(b.build())
            .build();
    }

    private MethodSpec buildMethod(OperationsMap ctx, CodegenOperation operation) {
        var tag = ctx.get("baseName").toString();
        var b = MethodSpec.methodBuilder(operation.operationId)
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addJavadoc(buildMethodJavadoc(ctx, operation));
        var additionalAnnotations = params.additionalContractAnnotations().get(tag);
        if (additionalAnnotations == null) {
            additionalAnnotations = params.additionalContractAnnotations().getOrDefault("*", List.of());
        }
        for (var additionalAnnotation : additionalAnnotations) {
            if (additionalAnnotation.annotation() != null && !additionalAnnotation.annotation().isBlank()) {
                // TODO parse text to to annotation spec
                throw new NotImplementedException();
            }
        }
        b.addAnnotation(AnnotationSpec.builder(Classes.httpRoute)
            .addMember("method", "$S", operation.httpMethod)
            .addMember("path", "$S", operation.path)
            .build()
        );
        this.buildImplicitHeaders(operation, b);
        for (var response : operation.responses) {
            b.addAnnotation(AnnotationSpec.builder(Classes.responseCodeMapper)
                .addMember("code", "$L", response.isDefault ? "-1" : response.code)
                .addMember("mapper", "$T.class", ClassName.get(apiPackage, ctx.get("classname") + "ClientResponseMappers", StringUtils.capitalize(operation.operationId) + response.code + "ApiResponseMapper"))
                .build()
            );
        }
        this.buildMethodAuth(operation, b);
        this.buildInterceptors(tag, b);
        if (operation.isDeprecated) {
            b.addAnnotation(Deprecated.class);
        }
        b.returns(ClassName.get(apiPackage, ctx.get("classname") + "Responses", StringUtils.capitalize(operation.operationId) + "ApiResponse"));
        for (var param : operation.allParams) {
            if (param.isFormParam) continue; // form params are handled separately
            b.addParameter(this.buildParameter(ctx, operation, param));
/*
{{#allParams}}
    {{^isFormParam}}
        {{#vendorExtensions.x-validate}}
            {{^isEnum}}
                {{#isModel}}@ru.tinkoff.kora.validation.common.annotation.Valid{{/isModel}}
            {{/isEnum}}
            {{#vendorExtensions.x-has-min-max}}@ru.tinkoff.kora.validation.common.annotation.Range(from = {{minimum}}, to = {{maximum}}, boundary = ru.tinkoff.kora.validation.common.annotation.Range.Boundary.{{#exclusiveMinimum}}EXCLUSIVE{{/exclusiveMinimum}}{{^exclusiveMinimum}}INCLUSIVE{{/exclusiveMinimum}}_{{#exclusiveMaximum}}EXCLUSIVE{{/exclusiveMaximum}}{{^exclusiveMaximum}}INCLUSIVE{{/exclusiveMaximum}}){{/vendorExtensions.x-has-min-max}}
            {{#vendorExtensions.x-has-min-max-items}}
            @ru.tinkoff.kora.validation.common.annotation.Size(min = {{minItems}}, max = {{maxItems}}){{/vendorExtensions.x-has-min-max-items}}{{#vendorExtensions.x-has-min-max-length}}
        @ru.tinkoff.kora.validation.common.annotation.Size(min = {{minLength}}, max = {{maxLength}}){{/vendorExtensions.x-has-min-max-length}}{{#vendorExtensions.x-has-pattern}}
        @ru.tinkoff.kora.validation.common.annotation.Pattern("{{{pattern}}}"){{/vendorExtensions.x-has-pattern}}
        {{/vendorExtensions.x-validate}}
        {{#isQueryParam}}
        @ru.tinkoff.kora.http.common.annotation.Query("{{baseName}}"){{/isQueryParam}}{{#isPathParam}}
        @ru.tinkoff.kora.http.common.annotation.Path("{{baseName}}"){{/isPathParam}}{{#isHeaderParam}}
        @ru.tinkoff.kora.http.common.annotation.Header("{{baseName}}"){{/isHeaderParam}}{{#isCookieParam}}
        @ru.tinkoff.kora.http.common.annotation.Cookie("{{baseName}}"){{/isCookieParam}}{{#isBodyParam}}
        {{#vendorExtensions.hasMapperTag}}
        @{{vendorExtensions.mapperTag}}
        {{/vendorExtensions.hasMapperTag}}{{/isBodyParam}}
        {{^required}}@Nullable {{/required}}{{{dataType}}} {{paramName}}{{#hasFormParams}},{{/hasFormParams}}{{^hasFormParams}}{{^-last}},{{/-last}}{{#-last}}
    {{/-last}}{{/hasFormParams}}
    {{/isFormParam}}
{{/allParams}}


    {{#hasFormParams}}{{#isClient}}
        @ru.tinkoff.kora.common.Mapping({{classname}}ClientRequestMappers.{{#lambda.titlecase}}{{operationId}}{{/lambda.titlecase}}FormParamRequestMapper.class){{/isClient}}{{^isClient}}
        @ru.tinkoff.kora.common.Mapping({{classname}}ServerRequestMappers.{{#lambda.titlecase}}{{operationId}}{{/lambda.titlecase}}FormParamRequestMapper.class){{/isClient}}
        {{#lambda.titlecase}}{{operationId}}{{/lambda.titlecase}}FormParam form
    {{/hasFormParams}}

 */
        }
        // todo parameters
        return b.build();
    }

    private ParameterSpec buildParameter(OperationsMap ctx, CodegenOperation operation, CodegenParameter param) {
        var type = asType(ctx, operation, param);
        if (!param.required) {
            type = type.box();
        }
        if (param.isFormParam) {
            var formParamClassName = ClassName.get(apiPackage, ctx.get("classname").toString(), StringUtils.capitalize(operation.operationId) + "FormParam");
            return ParameterSpec.builder(formParamClassName, "form")
                .addAnnotation(AnnotationSpec.builder(Classes.mapping)
                    .addMember("value", "$T.class", ClassName.get(apiPackage, ctx.get("classname") + "ClientRequestMappers", StringUtils.capitalize(operation.operationId) + "FormParamRequestMapper"))
                    .build()
                )
                .build();
        }
        var b = ParameterSpec.builder(type, param.paramName);
        var annotation = switch (param) {
            case CodegenParameter it when it.isQueryParam -> AnnotationSpec.builder(Classes.query)
                .addMember("value", "$S", param.baseName)
                .build();
            case CodegenParameter it when it.isPathParam -> AnnotationSpec.builder(Classes.path)
                .addMember("value", "$S", param.baseName)
                .build();
            case CodegenParameter it when it.isHeaderParam -> AnnotationSpec.builder(Classes.header)
                .addMember("value", "$S", param.baseName)
                .build();
            case CodegenParameter it when it.isCookieParam -> AnnotationSpec.builder(Classes.cookie)
                .addMember("value", "$S", param.baseName)
                .build();
            case CodegenParameter it when it.isBodyParam && KoraCodegen.isContentJson(param) -> AnnotationSpec.builder(ClassName.bestGuess(params.jsonAnnotation()))
                .build();
            case CodegenParameter it when it.isBodyParam -> null;
            default -> throw new IllegalStateException("Unexpected value: " + param);
        };
        if (annotation != null) {
            b.addAnnotation(annotation);
        }
        if (!param.required) {
            b.addAnnotation(Classes.nullable);
        }

        return b.build();
    }

    private void buildImplicitHeaders(CodegenOperation operation, MethodSpec.Builder b) {
        if (operation.implicitHeadersParams != null) {
            for (var implicitHeadersParam : operation.implicitHeadersParams) {
                var implicitParameters = AnnotationSpec.builder(ClassName.get("io.swagger.v3.oas.annotations", "Parameter"));
                implicitParameters
                    .addMember("name", "$S", implicitHeadersParam.baseName)
                    .addMember("description", "$S", Objects.requireNonNullElse(implicitHeadersParam.description, ""))
                    .addMember("required", "$L", implicitHeadersParam.required)
                    .addMember("in", "$T.HEADER", ClassName.get("io.swagger.v3.oas.annotations.enums", "ParameterIn"))
                ;
                b.addAnnotation(implicitParameters.build());
            }
        }
    }

    private void buildInterceptors(String tag, MethodSpec.Builder b) {
        var interceptors = params.interceptors().getOrDefault(tag, params.interceptors().get("*"));
        if (interceptors != null) {
            for (var interceptor : interceptors) {
                var type = interceptor.type() == null
                    ? Classes.httpClientInterceptor
                    : ClassName.bestGuess(interceptor.type());
                var interceptorTag = (String) interceptor.tag();
                var ann = AnnotationSpec
                    .builder(Classes.interceptWith)
                    .addMember("value", "$T.class", type);
                if (interceptorTag != null) {
                    ann.addMember("tag", "@$T($T.class)", Classes.tag, ClassName.bestGuess(interceptorTag));
                }
                b.addAnnotation(ann.build());
            }
        }
    }

    private void buildMethodAuth(CodegenOperation operation, MethodSpec.Builder b) {
        if (operation.hasAuthMethods) {
            if (params.authAsMethodArgument()) {
                // todo should be handled on parameters level
                throw new RuntimeException("TODO");
            }
            var authMethod = operation.authMethods.stream()
                .filter(a -> params.primaryAuth() == null || a.name.equals(params.primaryAuth()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Can't find OpenAPI securitySchema named: " + params.primaryAuth()));
            //                 var authName = camelize(toVarName(authMethod.name)); todo
            var authName = camelize(authMethod.name);
            b.addAnnotation(AnnotationSpec
                .builder(Classes.interceptWith)
                .addMember("value", "$T.class", Classes.httpClientInterceptor)
                .addMember("tag", "@$T($T.class)", Classes.tag, ClassName.get(apiPackage, "ApiSecurity", authName))
                .build()
            );
        }
    }

    private CodeBlock buildMethodJavadoc(OperationsMap ctx, CodegenOperation operation) {
        var b = CodeBlock.builder();
        b.add(operation.httpMethod + " " + operation.path);
        if (operation.summary != null) {
            b.add(": " + operation.summary);
        }
        b.add("\n");
        if (operation.notes != null) {
            b.add(operation.notes).add("\n");
        }
        for (var param : operation.allParams) {
            if (!param.isFormParam) {
                b.add("@param ").add(param.paramName).add(" ");
                if (param.description != null) {
                    b.add(param.description.trim());
                } else {
                    b.add(param.baseName);
                }
                if (param.required) {
                    b.add(" (required)");
                } else {
                    b.add(" (optional");
                    if (param.defaultValue != null) {
                        b.add(", default to ").add(param.defaultValue.trim());
                    }
                    b.add(")");
                }
                b.add("\n");
            }
        }
        if (operation.isDeprecated) {
            b.add("@deprecated\n");
        }
        if (operation.externalDocs != null) {
            b.add("@see <a href=\"" + operation.externalDocs.getUrl() + "\">" + operation.summary + " Documentation</a>");
        }
        return b.build();
    }

    private AnnotationSpec buildHttpClientAnnotation(OperationsMap ctx) {
        var httpClientAnnotation = AnnotationSpec.builder(Classes.httpClient);
        if (params.clientConfigPrefix() != null) {
            httpClientAnnotation.addMember("configPath", "$S", params.clientConfigPrefix() + "." + ctx.get("classname"));
        }
        var tag = ctx.get("baseName").toString();
        var clientTag = params.clientTags().get(tag);
        var defaultTag = params.clientTags().get("*");
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
