package io.koraframework.openapi.generator.javagen;

import com.palantir.javapoet.*;
import io.koraframework.openapi.generator.AbstractGenerator;
import io.koraframework.openapi.generator.CodegenParams;
import io.koraframework.openapi.generator.KoraCodegen;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.IJsonSchemaValidationProperties;
import org.openapitools.codegen.model.OperationsMap;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

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
                type = type.box().annotated(AnnotationSpec.builder(Classes.nullable).build());
            }
            var p = ParameterSpec.builder(type, formParam.paramName);
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

    protected List<AnnotationSpec> buildInterceptors(OperationsMap ctx, CodegenOperation operation, ClassName defaultInterceptorType) {
        var extensions = resolveExtensions(ctx, operation);
        var result = new ArrayList<AnnotationSpec>();
        for (var extension : extensions) {
            if (extension.interceptorType() == null && extension.interceptorTag().isEmpty()) {
                continue;
            }
            var type = extension.interceptorType() == null
                ? defaultInterceptorType
                : ClassName.bestGuess(extension.interceptorType());
            if (extension.interceptorTag().isEmpty()) {
                result.add(AnnotationSpec.builder(Classes.interceptWith)
                    .addMember("value", "$T.class", type)
                    .build());
            } else {
                for (var interceptorTag : extension.interceptorTag()) {
                    result.add(AnnotationSpec.builder(Classes.interceptWith)
                        .addMember("value", "$T.class", type)
                        .addMember("tag", "$T.class", ClassName.bestGuess(interceptorTag))
                        .build());
                }
            }
        }
        return result;
    }

    protected ParameterSpec buildParameter(OperationsMap ctx, CodegenOperation operation, CodegenParameter param) {
        var type = asType(ctx, operation, param);
        if (!param.required) {
            type = type.box().annotated(AnnotationSpec.builder(Classes.nullable).build());
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
        if (param.isBodyParam && KoraCodegen.isContentJson(param) && !isBareObject(param)) {
            b.addAnnotation(jsonAnnotation());
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
            b.add(" : " + operation.summary);
        }
        b.add("\n");
        if (operation.notes != null) {
            b.add(operation.notes).add("\n");
        }
        b.add("\n");
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
        if (!operation.responses.isEmpty()) {
            b.add("@return ");
            for (var i = 0; i < operation.responses.size(); i++) {
                if (i > 0) {
                    b.add(" or ");
                }
                var response = operation.responses.get(i);
                b.add(Objects.requireNonNullElse(response.message, ""));
                b.add(" (status code ");
                b.add(response.isDefault ? "default" : response.code);
                b.add(")");
            }
            b.add("\n");
        }
        if (operation.isDeprecated) {
            b.add("@deprecated\n");
        }
        if (operation.externalDocs != null) {
            b.add("@see <a href=\"" + operation.externalDocs.getUrl() + "\">" + operation.summary + " Documentation</a>");
        }
        return b.build();
    }

    protected List<AnnotationSpec> buildAdditionalMethodAnnotations(OperationsMap ctx, CodegenOperation operation) {
        var result = new ArrayList<AnnotationSpec>();
        var configPath = params.codegenMode.isClient()
            ? clientConfigPath(ctx.get("classname").toString())
            : serverConfigPath(ctx.get("classname") + "Controller");
        for (var extension : resolveExtensions(ctx, operation)) {
            addAnnotations(result, extension.additionalMethodAnnotations(), configPath);
        }
        return result;
    }

    protected List<AnnotationSpec> buildAdditionalModelTypeAnnotations() {
        var result = new ArrayList<AnnotationSpec>();
        addTypeAnnotations(result, extension -> {
            addAnnotations(result, extension.additionalTypeAnnotations(), null);
            addAnnotations(result, extension.additionalModelTypeAnnotations(), null);
        });
        return result;
    }

    protected List<AnnotationSpec> buildAdditionalEnumTypeAnnotations() {
        var result = new ArrayList<AnnotationSpec>();
        addTypeAnnotations(result, extension -> {
            addAnnotations(result, extension.additionalTypeAnnotations(), null);
            addAnnotations(result, extension.additionalEnumTypeAnnotations(), null);
        });
        return result;
    }

    private void addTypeAnnotations(List<AnnotationSpec> result, Consumer<CodegenParams.GeneratorExtension> consumer) {
        if (params.extensions.global() != null) {
            consumer.accept(params.extensions.global());
        }
    }

    private void addAnnotations(List<AnnotationSpec> result, List<String> annotations, @Nullable String configPath) {
        for (var annotation : annotations) {
            if (annotation != null && !annotation.isBlank()) {
                result.add(parseAnnotation(annotation, configPath));
            }
        }
    }

    protected List<CodegenParams.GeneratorExtension> resolveExtensions(OperationsMap ctx, CodegenOperation operation) {
        var result = new ArrayList<CodegenParams.GeneratorExtension>();
        if (params.extensions.global() != null) {
            result.add(params.extensions.global());
        }
        var tagExtension = params.extensions.tags().get(ctx.get("baseName").toString());
        if (tagExtension != null) {
            result.add(tagExtension);
        }
        var operationExtension = params.extensions.operations().get(operation.operationId);
        if (operationExtension != null) {
            result.add(operationExtension);
        }
        return result;
    }

    private AnnotationSpec parseAnnotation(String annotation, @Nullable String configPath) {
        var value = configPath == null ? annotation : annotation.replace("%{configPath}", configPath);
        value = value.strip();
        if (value.startsWith("@")) {
            value = value.substring(1);
        }
        var argumentsStart = value.indexOf('(');
        if (argumentsStart < 0) {
            return AnnotationSpec.builder(ClassName.bestGuess(value)).build();
        }
        var type = value.substring(0, argumentsStart).strip();
        var arguments = value.substring(argumentsStart + 1, value.lastIndexOf(')')).strip();
        var builder = AnnotationSpec.builder(ClassName.bestGuess(type));
        if (!arguments.isBlank()) {
            for (var argument : splitAnnotationArguments(arguments)) {
                var eq = argument.indexOf('=');
                if (eq < 0) {
                    builder.addMember("value", "$L", argument.strip());
                } else {
                    builder.addMember(argument.substring(0, eq).strip(), "$L", argument.substring(eq + 1).strip());
                }
            }
        }
        return builder.build();
    }

    private List<String> splitAnnotationArguments(String arguments) {
        var result = new ArrayList<String>();
        var start = 0;
        var depth = 0;
        var inString = false;
        for (int i = 0; i < arguments.length(); i++) {
            var c = arguments.charAt(i);
            if (c == '"' && (i == 0 || arguments.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString && (c == '(' || c == '{' || c == '[')) {
                depth++;
            } else if (!inString && (c == ')' || c == '}' || c == ']')) {
                depth--;
            } else if (!inString && depth == 0 && c == ',') {
                result.add(arguments.substring(start, i));
                start = i + 1;
            }
        }
        result.add(arguments.substring(start));
        return result;
    }

    private String clientConfigPath(String clientName) {
        if (params.clientConfigPrefix != null && !params.clientConfigPrefix.isBlank()) {
            return params.clientConfigPrefix + "." + StringUtils.uncapitalize(clientName);
        }
        return params.clientConfig;
    }

    private String serverConfigPath(String controllerTypeName) {
        return params.serverConfigPrefix.replace("%{ControllerTypeNameInCamelCase}", StringUtils.uncapitalize(controllerTypeName));
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
