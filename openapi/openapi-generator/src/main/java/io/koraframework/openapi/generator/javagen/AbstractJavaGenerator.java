package io.koraframework.openapi.generator.javagen;

import com.palantir.javapoet.*;
import io.koraframework.openapi.generator.AbstractGenerator;
import io.koraframework.openapi.generator.CodegenParams;
import io.koraframework.openapi.generator.KoraCodegen;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
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
        return buildFormParamsRecord(ctx, operation, false);
    }

    protected TypeSpec buildFormParamsRecord(OperationsMap ctx, CodegenOperation operation, boolean validate) {
        var b = MethodSpec.constructorBuilder();
        for (var formParam : operation.formParams) {
            var type = formParam.isFile
                ? (formParam.isArray ? ParameterizedTypeName.get(ClassName.get(List.class), Classes.formPart) : Classes.formPart)
                : asType(ctx, operation, formParam);
            if (!formParam.required) {
                type = type.box().annotated(AnnotationSpec.builder(Classes.nullable).build());
            }
            if (validate && !formParam.isFile) {
                type = withItemsValidation(type, formParam, "operation `" + operation.operationId + "`");
            }
            var p = ParameterSpec.builder(type, formParam.paramName);
            if (formParam.description != null) {
                p.addJavadoc("$L ", formParam.description);
            }
            if (formParam.required) {
                p.addJavadoc("(required)");
            } else if (formParam.defaultValue != null) {
                p.addJavadoc("(optional, default to $L)", formParam.defaultValue);
            } else {
                p.addJavadoc("(optional)");
            }
            if (validate && !formParam.isFile) {
                p.addAnnotations(getValidation(formParam, "operation `" + operation.operationId + "`"));
            }

            b.addParameter(p.build());
        }

        var t = TypeSpec.recordBuilder(StringUtils.capitalize(operation.operationId) + "FormParam")
            .addAnnotation(generated())
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC);
        if (validate) {
            t.addAnnotation(Classes.valid);
        }
        return t
            .recordConstructor(b.build())
            .build();
    }


    protected AnnotationSpec securityTagAnnotation(String tag) {
        return AnnotationSpec.builder(Classes.tag)
            .addMember("value", "$T.class", ClassName.get(apiPackage, "ApiSecurity", tag)).build();
    }

    // the type a property of a form object is converted from or to: the element type of an array, the nested class of an inline enum
    protected TypeName formObjectPropertyValueType(CodegenModel model, CodegenProperty property) {
        var value = property.isArray ? property.items : property;
        if (property.isInnerEnum) {
            return ClassName.get(modelPackage, model.classname, value.enumName);
        }
        return asType(value).box().withoutAnnotations();
    }

    protected String formObjectConverterName(CodegenParameter p, CodegenProperty property) {
        return p.paramName + capitalize(property.name) + "Converter";
    }

    protected AnnotationSpec formPartTagAnnotation(String tag) {
        return AnnotationSpec.builder(Classes.tag)
            .addMember("value", "$T.class", ClassName.get(apiPackage, FormPartsModuleGenerator.CLASS_NAME, tag)).build();
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
            throw new IllegalArgumentException("""
                Kora internal error: form parameter `%s` reached regular parameter generation.

                Form parameters must be grouped into generated form DTOs and handled by request mapper generators.
                Please report this with operation `%s`.
                """.formatted(param.paramName, operation.operationId));
        }
        if (params.codegenMode.isServer() && params.enableValidation) {
            type = withItemsValidation(type, param, "operation `" + operation.operationId + "`");
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
        if (param.isBodyParam && KoraCodegen.isContentJson(param) && requiresJsonMapper(param)) {
            b.addAnnotation(jsonAnnotation());
        } else if (param.isBodyParam && params.codegenMode.isClient() && customBodyContentType(param) != null) {
            var mapper = ClassName.get(apiPackage, ctx.get("classname") + "ClientRequestMappers", capitalize(operation.operationId) + "BodyParamRequestMapper");
            b.addAnnotation(AnnotationSpec.builder(Classes.mapping)
                .addMember("value", "$T.class", mapper)
                .build());
        }
        if (params.codegenMode.isServer() && params.enableValidation) {
            b.addAnnotations(getValidation(param, "operation `" + operation.operationId + "`"));
        }
        return b.build();
    }

    protected AnnotationSpec jsonAnnotation() {
        return AnnotationSpec.builder(Classes.json).build();
    }

    /**
     * Puts the constraints of array items and map values on the type arguments, like {@code List<@Size(max = 5) String>}
     */
    protected TypeName withItemsValidation(TypeName type, IJsonSchemaValidationProperties variable, String owner) {
        var items = variable.getItems();
        if (items == null || !(type instanceof ParameterizedTypeName parameterized)) {
            return type;
        }
        var typeArguments = new ArrayList<>(parameterized.typeArguments());
        if (parameterized.rawType().equals(Classes.jsonNullable)) {
            typeArguments.set(0, withItemsValidation(typeArguments.get(0), variable, owner));
        } else if (variable.getIsArray() || variable.getIsMap()) {
            var itemType = withItemsValidation(typeArguments.get(typeArguments.size() - 1), items, owner);
            // models are validated by @Valid of the container itself
            var itemValidation = getValidation(items, owner).stream()
                .filter(annotation -> !annotation.type().equals(Classes.valid))
                .toList();
            if (!itemValidation.isEmpty()) {
                itemType = itemType.annotated(itemValidation);
            }
            typeArguments.set(typeArguments.size() - 1, itemType);
        } else {
            return type;
        }
        return ParameterizedTypeName.get((ClassName) parameterized.rawType().withoutAnnotations(), typeArguments.toArray(TypeName[]::new)).annotated(parameterized.annotations());
    }

    protected List<AnnotationSpec> getValidation(IJsonSchemaValidationProperties variable, String owner) {
        var result = new ArrayList<AnnotationSpec>(2);
        warnIgnoredStringValidation(variable, owner);
        if (variable.getMinimum() != null || variable.getMaximum() != null) {
            var singleBound = singleBoundValidation(variable);
            if (singleBound != null) {
                result.add(singleBound);
            } else {
                result.add(AnnotationSpec.builder(Classes.range)
                    .addMember("from", rangeBound(variable, variable.getMinimum(), true))
                    .addMember("to", rangeBound(variable, variable.getMaximum(), false))
                    .addMember("boundary", "$T.$L_$L", Classes.boundary, variable.getExclusiveMinimum() ? "EXCLUSIVE" : "INCLUSIVE", variable.getExclusiveMaximum() ? "EXCLUSIVE" : "INCLUSIVE")
                    .build());
            }
        }
        if ((variable.getMinLength() != null || variable.getMaxLength() != null) && isValidatedAsString(variable)) {
            var size = AnnotationSpec.builder(Classes.size);
            if (variable.getMinLength() != null) {
                size.addMember("min", "$L", variable.getMinLength());
            }
            if (variable.getMaxLength() != null) {
                size.addMember("max", "$L", variable.getMaxLength());
            } else {
                size.addMember("max", "$T.MAX_VALUE", Integer.class);
            }
            result.add(size.build());
        }
        if (variable.getMaxItems() != null || variable.getMinItems() != null) {
            var size = AnnotationSpec.builder(Classes.size);
            if (variable.getMinItems() != null) {
                size.addMember("min", "$L", variable.getMinItems());
            }
            if (variable.getMaxItems() != null) {
                size.addMember("max", "$L", variable.getMaxItems());
            } else {
                size.addMember("max", "$T.MAX_VALUE", Integer.class);
            }
            result.add(size.build());
        }
        if (variable.getPattern() != null && isValidatedAsString(variable)) {
            result.add(AnnotationSpec.builder(Classes.pattern)
                .addMember("value", "$S", variable.getPattern())
                .build());
        }
        if (variable.getIsModel() || hasModelItems(variable)) {
            result.add(AnnotationSpec.builder(Classes.valid).build());
        }
        return result;
    }

    private static CodeBlock rangeBound(IJsonSchemaValidationProperties variable, @Nullable String bound, boolean lower) {
        if (bound != null) {
            return bound.contains(".") ? CodeBlock.of("$L", bound) : CodeBlock.of("$L.0", bound);
        }
        if (variable.getIsLong()) {
            return CodeBlock.of("$L.0", lower ? Long.MIN_VALUE : Long.MAX_VALUE);
        }
        if (variable.getIsInteger()) {
            return CodeBlock.of("$L.0", lower ? Integer.MIN_VALUE : Integer.MAX_VALUE);
        }
        if (variable.getIsDouble() || variable.getIsFloat() || isBigNumber(variable)) {
            return lower ? CodeBlock.of("-$T.MAX_VALUE", Double.class) : CodeBlock.of("$T.MAX_VALUE", Double.class);
        }
        throw new IllegalArgumentException(invalidNumericValidationTypeError(variable));
    }

    private static boolean isBigNumber(IJsonSchemaValidationProperties variable) {
        var dataType = variable.getDataType();
        return dataType != null && (dataType.endsWith("BigDecimal") || dataType.endsWith("BigInteger"));
    }

    @Nullable
    private AnnotationSpec singleBoundValidation(IJsonSchemaValidationProperties variable) {
        if (!(variable.getIsInteger() || variable.getIsLong() || isBigNumber(variable)) || (variable.getMinimum() != null) == (variable.getMaximum() != null)) {
            return null;
        }
        try {
            if (variable.getMinimum() != null) {
                var value = new java.math.BigDecimal(variable.getMinimum()).longValueExact();
                if (variable.getExclusiveMinimum()) {
                    if (value == 0) return AnnotationSpec.builder(Classes.positive).build();
                    value = Math.incrementExact(value);
                } else if (value == 0) {
                    return AnnotationSpec.builder(Classes.positiveOrZero).build();
                }
                return AnnotationSpec.builder(Classes.min).addMember("value", "$LL", value).build();
            }
            var value = new java.math.BigDecimal(variable.getMaximum()).longValueExact();
            if (variable.getExclusiveMaximum()) {
                if (value == 0) return AnnotationSpec.builder(Classes.negative).build();
                value = Math.decrementExact(value);
            } else if (value == 0) {
                return AnnotationSpec.builder(Classes.negativeOrZero).build();
            }
            return AnnotationSpec.builder(Classes.max).addMember("value", "$LL", value).build();
        } catch (ArithmeticException e) {
            return null;
        }
    }

    private static String invalidNumericValidationTypeError(IJsonSchemaValidationProperties variable) {
        return """
            Invalid OpenAPI numeric validation schema.

            Schema declares `minimum` or `maximum`, but generated type is not a supported numeric type.
            Schema dataType: `%s`
            Schema format: `%s`

            Fix: use numeric schema type/format for min/max constraints, or remove `minimum` / `maximum`.
            """.formatted(variable.getDataType(), variable.getFormat());
    }


    protected CodeBlock buildMethodJavadoc(OperationsMap ctx, CodegenOperation operation) {
        var b = CodeBlock.builder();
        b.add("$L $L", operation.httpMethod, operation.path);
        if (operation.summary != null) {
            b.add(" : $L", operation.summary);
        }
        b.add("\n");
        if (operation.notes != null) {
            b.add("$L\n", operation.notes);
        }
        b.add("\n");
        for (var param : operation.allParams) {
            if (!param.isFormParam) {
                b.add("@param $L ", param.paramName);
                if (param.description != null) {
                    b.add("$L", param.description.trim());
                } else {
                    b.add("$L", param.baseName);
                }
                if (param.required) {
                    b.add(" (required)");
                } else {
                    b.add(" (optional");
                    if (param.defaultValue != null) {
                        b.add(", default to $L", param.defaultValue.trim());
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
                    b.add("\n        ");
                }
                var response = operation.responses.get(i);
                b.add("$L (status code $L)", Objects.requireNonNullElse(response.message, ""), response.isDefault ? "default" : response.code);
            }
            b.add("\n");
        }
        if (operation.isDeprecated) {
            b.add("@deprecated\n");
        }
        if (operation.externalDocs != null) {
            b.add("@see <a href=\"$L\">$L Documentation</a>", operation.externalDocs.getUrl(), operation.summary);
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
        addTypeAnnotations(extension -> {
            addAnnotations(result, extension.additionalTypeAnnotations(), null);
            addAnnotations(result, extension.additionalModelTypeAnnotations(), null);
        });
        return result;
    }

    protected List<AnnotationSpec> buildAdditionalEnumTypeAnnotations() {
        var result = new ArrayList<AnnotationSpec>();
        addTypeAnnotations(extension -> {
            addAnnotations(result, extension.additionalTypeAnnotations(), null);
            addAnnotations(result, extension.additionalEnumTypeAnnotations(), null);
        });
        return result;
    }

    private void addTypeAnnotations(Consumer<CodegenParams.GeneratorExtension> consumer) {
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
