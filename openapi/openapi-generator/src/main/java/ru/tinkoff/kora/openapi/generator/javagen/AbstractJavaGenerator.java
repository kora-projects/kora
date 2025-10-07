package ru.tinkoff.kora.openapi.generator.javagen;

import com.palantir.javapoet.*;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.IJsonSchemaValidationProperties;
import org.openapitools.codegen.model.OperationsMap;
import ru.tinkoff.kora.openapi.generator.AbstractGenerator;
import ru.tinkoff.kora.openapi.generator.KoraCodegen;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class AbstractJavaGenerator<C> extends AbstractGenerator<C, JavaFile> {
    protected AnnotationSpec generated() {
        return AnnotationSpec.builder(Classes.generated).addMember("value", "$S", this.getClass().getCanonicalName()).build();
    }

    protected TypeSpec buildFormParamsRecord(OperationsMap ctx, CodegenOperation operation) {
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
            .addAnnotation(generated())
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .recordConstructor(b.build())
            .build();
    }


    protected AnnotationSpec securityTagAnnotation(String tag) {
        return AnnotationSpec.builder(Classes.tag)
            .addMember("value", "$T.class", ClassName.get(apiPackage, "ApiSecurity", tag)).build();
    }

    protected List<AnnotationSpec> buildInterceptors(String tag, ClassName defaultInterceptorType) {
        var interceptors = params.interceptors.getOrDefault(tag, params.interceptors.get("*"));
        if (interceptors == null) {
            return List.of();
        }
        var result = new ArrayList<AnnotationSpec>();
        for (var interceptor : interceptors) {
            var type = interceptor.type() == null
                ? defaultInterceptorType
                : ClassName.bestGuess(interceptor.type());
            var interceptorTag = (String) interceptor.tag();
            var ann = AnnotationSpec
                .builder(Classes.interceptWith)
                .addMember("value", "$T.class", type);
            if (interceptorTag != null) {
                ann.addMember("tag", "@$T($T.class)", Classes.tag, ClassName.bestGuess(interceptorTag));
            }
            result.add(ann.build());
        }
        return result;
    }

    protected ParameterSpec buildParameter(OperationsMap ctx, CodegenOperation operation, CodegenParameter param) {
        var type = asType(ctx, operation, param);
        if (!param.required) {
            type = type.box();
        }
        if (param.isFormParam) {
            throw new IllegalArgumentException("Form parameters should be handled separately");
        }
        var b = ParameterSpec.builder(type, param.paramName);
        if (param.isQueryParam) {
            b.addAnnotation(AnnotationSpec.builder(Classes.query)
                .addMember("value", "$S", param.baseName)
                .build());
        }
        if (param.isPathParam) {
            b.addAnnotation(AnnotationSpec.builder(Classes.path)
                .addMember("value", "$S", param.baseName)
                .build());
        }
        if (param.isHeaderParam) {
            b.addAnnotation(AnnotationSpec.builder(Classes.header)
                .addMember("value", "$S", param.baseName)
                .build());
        }
        if (param.isCookieParam) {
            b.addAnnotation(AnnotationSpec.builder(Classes.cookie)
                .addMember("value", "$S", param.baseName)
                .build());
        }
        if (param.isBodyParam && KoraCodegen.isContentJson(param)) {
            b.addAnnotation(jsonAnnotation());
        }
        if (!param.required) {
            b.addAnnotation(Classes.nullable);
        }
        if (params.codegenMode.isServer() && params.enableValidation) {
            var validation = getValidation(param);
            b.addAnnotation(validation);
        }
        return b.build();
    }

    protected AnnotationSpec jsonAnnotation() {
        return AnnotationSpec.builder(Classes.json).build();
    }

    @Nullable
    protected AnnotationSpec getValidation(IJsonSchemaValidationProperties variable) {
        if (variable.getMinimum() != null || variable.getMaximum() != null) {
            CodeBlock minimum;
            if (variable.getMinimum() != null) {
                if (!variable.getMinimum().contains(".")) {
                    minimum = CodeBlock.of("$L.0", variable.getMinimum());
                } else {
                    minimum = CodeBlock.of("$L", variable.getMinimum());
                }
            } else {
                if (variable.getIsLong()) {
                    minimum = CodeBlock.of("$L.0", Long.MIN_VALUE);
                } else if (variable.getIsInteger()) {
                    minimum = CodeBlock.of("$L.0", Integer.MIN_VALUE);
                } else if (variable.getIsDouble()) {
                    minimum = CodeBlock.of("$T.MIN_VALUE", Double.class);
                } else if (variable.getIsFloat()) {
                    minimum = CodeBlock.of("$T.MIN_VALUE", Float.class);
                } else {
                    throw new IllegalArgumentException("Invalid minimum variable type value: " + variable);
                }
            }
            CodeBlock maximum;
            if (variable.getMinimum() != null) {
                if (!variable.getMinimum().contains(".")) {
                    maximum = CodeBlock.of("$L.0", variable.getMinimum());
                } else {
                    maximum = CodeBlock.of("$L", variable.getMinimum());
                }
            } else {
                if (variable.getIsLong()) {
                    maximum = CodeBlock.of("$L.0", Long.MAX_VALUE);
                } else if (variable.getIsInteger()) {
                    maximum = CodeBlock.of("$L.0", Integer.MAX_VALUE);
                } else if (variable.getIsDouble()) {
                    maximum = CodeBlock.of("$T.MAX_VALUE", Double.class);
                } else if (variable.getIsFloat()) {
                    maximum = CodeBlock.of("$T.MAX_VALUE", Float.class);
                } else {
                    throw new IllegalArgumentException("Invalid minimum variable type value: " + variable);
                }
            }
            return AnnotationSpec.builder(Classes.range)
                .addMember("from", minimum)
                .addMember("to", maximum)
                .addMember("boundary", "$T.$L_$L", Classes.boundary, variable.getExclusiveMinimum() ? "EXCLUSIVE" : "INCLUSIVE", variable.getExclusiveMaximum() ? "EXCLUSIVE" : "INCLUSIVE")
                .build();
        }
        if (variable.getMinLength() != null || variable.getMaxLength() != null) {
            return AnnotationSpec.builder(Classes.size)
                .addMember("min", "$L", variable.getMinLength() != null ? variable.getMinLength() : 0)
                .addMember("max", "$L", variable.getMaxLength() != null ? variable.getMaxLength() : Integer.MAX_VALUE)
                .build();
        }
        if (variable.getMaxItems() != null || variable.getMinItems() != null) {
            return AnnotationSpec.builder(Classes.size)
                .addMember("min", "$L", variable.getMinItems() != null ? variable.getMinItems() : 0)
                .addMember("max", "$L", variable.getMaxItems() != null ? variable.getMaxItems() : Integer.MAX_VALUE)
                .build();
        }
        if (variable.getPattern() != null) {
            return AnnotationSpec.builder(Classes.pattern)
                .addMember("value", "$S", variable.getPattern())
                .build();
        }
        if (variable.getIsModel()) {
            return AnnotationSpec.builder(Classes.valid).build();
        }
        return null;
    }


    protected CodeBlock buildMethodJavadoc(OperationsMap ctx, CodegenOperation operation) {
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

    protected List<AnnotationSpec> buildAdditionalAnnotations(String tag) {
        var result = new ArrayList<AnnotationSpec>();
        var additionalAnnotations = params.additionalContractAnnotations.get(tag);
        if (additionalAnnotations == null) {
            additionalAnnotations = params.additionalContractAnnotations.getOrDefault("*", List.of());
        }
        for (var additionalAnnotation : additionalAnnotations) {
            if (additionalAnnotation.annotation() != null && !additionalAnnotation.annotation().isBlank()) {
                // TODO parse text to to annotation spec
                throw new NotImplementedException();
            }
        }
        return result;
    }

    protected List<AnnotationSpec> buildImplicitHeaders(CodegenOperation operation) {
        if (operation.implicitHeadersParams == null) {
            return List.of();
        }
        var result = new ArrayList<AnnotationSpec>();
        for (var implicitHeadersParam : operation.implicitHeadersParams) {
            var implicitParameters = AnnotationSpec.builder(ClassName.get("io.swagger.v3.oas.annotations", "Parameter"));
            implicitParameters
                .addMember("name", "$S", implicitHeadersParam.baseName)
                .addMember("description", "$S", Objects.requireNonNullElse(implicitHeadersParam.description, ""))
                .addMember("required", "$L", implicitHeadersParam.required)
                .addMember("in", "$T.HEADER", ClassName.get("io.swagger.v3.oas.annotations.enums", "ParameterIn"))
            ;
            result.add(implicitParameters.build());
        }
        return result;
    }


    protected AnnotationSpec buildHttpRoute(CodegenOperation operation) {
        return AnnotationSpec.builder(Classes.httpRoute)
            .addMember("method", "$S", operation.httpMethod)
            .addMember("path", "$S", operation.path)
            .build();
    }
}
