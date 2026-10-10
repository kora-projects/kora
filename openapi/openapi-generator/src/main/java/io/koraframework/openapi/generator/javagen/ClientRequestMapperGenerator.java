package io.koraframework.openapi.generator.javagen;

import com.palantir.javapoet.*;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.model.OperationsMap;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Objects;

import static io.koraframework.openapi.generator.KoraCodegen.isContentJson;

public class ClientRequestMapperGenerator extends AbstractJavaGenerator<OperationsMap> {

    public static final ClassName URL_ENCODED_WRITER = ClassName.get("io.koraframework.http.client.common.request.form", "FormUrlEncodedWriter");

    @Override
    public JavaFile generate(OperationsMap ctx) {
        var className = ClassName.get(apiPackage, ctx.get("classname") + "ClientRequestMappers");
        var b = TypeSpec.interfaceBuilder(className)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(generated());
        for (var operation : ctx.getOperations().getOperation()) {
            if (customBodyContentType(operation.bodyParam) != null) {
                b.addType(buildBodyParamMapper(className, operation));
            }
            if (!operation.getHasFormParams()) {
                continue;
            }
            b.addType(buildFormMapper(ctx, className, operation));
        }

        return JavaFile.builder(apiPackage, b.build()).build();
    }

    private TypeSpec buildBodyParamMapper(ClassName rootName, CodegenOperation operation) {
        var bodyParam = operation.bodyParam;
        var contentType = customBodyContentType(bodyParam);
        var className = rootName.nestedClass(capitalize(operation.operationId) + "BodyParamRequestMapper");
        var valueType = asType(bodyParam);
        if (!bodyParam.required) {
            valueType = valueType.box().annotated(AnnotationSpec.builder(Classes.nullable).build());
        }
        var apply = MethodSpec.methodBuilder("apply")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .returns(Classes.httpBodyOutput)
            .addParameter(valueType, "value")
            .addException(Exception.class);
        if (!bodyParam.required) {
            apply.beginControlFlow("if (value == null)")
                .addStatement("return $T.empty()", Classes.httpBody)
                .endControlFlow();
        }
        if (bodyParam.isBinary) {
            apply.addStatement("return $T.of($S, value)", Classes.httpBody, contentType);
        } else {
            apply.addStatement("return $T.of($S, value.getBytes($T.UTF_8))", Classes.httpBody, contentType, ClassName.get(java.nio.charset.StandardCharsets.class));
        }

        return TypeSpec.classBuilder(className)
            .addAnnotation(generated())
            .addAnnotation(Classes.defaultComponent)
            .addAnnotation(Classes.component)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .addSuperinterface(ParameterizedTypeName.get(Classes.httpClientRequestMapper, valueType.withoutAnnotations()))
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC).build())
            .addMethod(apply.build())
            .build();
    }

    private TypeSpec buildFormMapper(OperationsMap ctx, ClassName rootName, CodegenOperation operation) {
        var className = rootName.nestedClass(capitalize(operation.operationId) + "FormParamRequestMapper");
        var formParamClassName = ClassName.get(apiPackage, ctx.get("classname").toString(), capitalize(operation.operationId) + "FormParam");
        var b = TypeSpec.classBuilder(className)
            .addAnnotation(generated())
            .addAnnotation(Classes.defaultComponent)
            .addAnnotation(Classes.component)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addSuperinterface(ParameterizedTypeName.get(Classes.httpClientRequestMapper, formParamClassName));
        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);
        var apply = MethodSpec.methodBuilder("apply")
            .returns(Classes.httpBodyOutput)
            .addParameter(formParamClassName, "value")
            .addException(Exception.class)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class);
        var urlEncodedForm = operation.consumes != null && operation.consumes.stream()
            .map(m -> m.get("mediaType"))
            .anyMatch("application/x-www-form-urlencoded"::equalsIgnoreCase);
        var multipartForm = operation.consumes != null && operation.consumes.stream()
            .map(m -> m.get("mediaType"))
            .anyMatch("multipart/form-data"::equalsIgnoreCase);
        if (urlEncodedForm && multipartForm) {
            throw new IllegalArgumentException(ambiguousFormContentTypeError(operation));
        }
        for (var p : operation.formParams) {
            var formObject = urlEncodedForm ? explodedFormObject(operation, p) : null;
            if (formObject != null) {
                // an object of a url-encoded form is a field per property, each written with a converter of the property type
                for (var property : formObject.allVars) {
                    var valueType = formObjectPropertyValueType(formObject, property);
                    if (valueType.equals(ClassName.get(String.class))) {
                        continue;
                    }
                    var mapperType = ParameterizedTypeName.get(Classes.stringParameterConverter, valueType);
                    var converterName = formObjectConverterName(p, property);
                    constructor.addParameter(mapperType, converterName)
                        .addStatement("this.$N = $N", converterName, converterName);
                    b.addField(mapperType, converterName, Modifier.PRIVATE, Modifier.FINAL);
                }
                continue;
            }
            if (needsConverter(p)) {
                // an array is written element by element, so its converter is over the element type
                var valueType = isConvertibleArray(p) ? elementType(p) : asType(p);
                var mapperType = ParameterizedTypeName.get(Classes.stringParameterConverter, valueType.box());
                var param = ParameterSpec.builder(mapperType, p.paramName + "Converter");
                // a JSON part uses the @Json writer, any other declared media type has a tag of its own in ApiFormPartsModule
                var tag = formPartTag(p);
                if (isJsonFormPart(p)) {
                    param.addAnnotation(Classes.json);
                } else if (tag != null) {
                    param.addAnnotation(formPartTagAnnotation(tag));
                }
                constructor.addParameter(param.build())
                    .addStatement("this.$N = $N", p.paramName + "Converter", p.paramName + "Converter");
                b.addField(mapperType, p.paramName + "Converter", Modifier.PRIVATE, Modifier.FINAL);
            }
        }
        if (urlEncodedForm) {
            apply.addStatement("var b = new $T()", URL_ENCODED_WRITER);
            for (var formParam : operation.formParams) {
                // a required primitive record component is never null, so guarding it would not compile
                var nullChecked = !isRequiredPrimitive(formParam);
                if (nullChecked) {
                    apply.beginControlFlow("if (value.$N() != null)", formParam.paramName);
                }
                var formObject = explodedFormObject(operation, formParam);
                if (formObject != null) {
                    var object = "_" + formParam.paramName;
                    apply.addStatement("var $N = value.$N()", object, formParam.paramName);
                    for (var property : formObject.allVars) {
                        // an optional nullable property is a JsonNullable, an optional or a nullable one is a nullable component
                        var jsonNullable = property.isNullable && !property.required;
                        var propertyValue = jsonNullable
                            ? CodeBlock.of("$N.$N().value()", object, property.name)
                            : CodeBlock.of("$N.$N()", object, property.name);
                        if (jsonNullable) {
                            apply.beginControlFlow("if ($N.$N().isDefined() && $L != null)", object, property.name, propertyValue);
                        } else if (!property.required || property.isNullable) {
                            apply.beginControlFlow("if ($L != null)", propertyValue);
                        }
                        var item = property.isArray ? CodeBlock.of("item") : propertyValue;
                        var converted = formObjectPropertyValueType(formObject, property).equals(ClassName.get(String.class))
                            ? item
                            : CodeBlock.of("$N.convert($L)", formObjectConverterName(formParam, property), item);
                        if (property.isArray) {
                            apply.beginControlFlow("for (var item : $L)", propertyValue)
                                .addStatement("b.add($S, $L)", property.baseName, converted)
                                .endControlFlow();
                        } else {
                            apply.addStatement("b.add($S, $L)", property.baseName, converted);
                        }
                        if (jsonNullable || !property.required || property.isNullable) {
                            apply.endControlFlow();
                        }
                    }
                } else if (isConvertibleArray(formParam)) {
                    var item = needsConverter(formParam)
                        ? CodeBlock.of("$N.convert(item)", formParam.paramName + "Converter")
                        : CodeBlock.of("item");
                    var delimiter = urlEncodedArrayDelimiter(formParam);
                    if (delimiter == null) {
                        // multiple values are sent as repeated same-named fields, one per element
                        apply.beginControlFlow("for (var item : value.$N())", formParam.paramName)
                            .addStatement("b.add($S, $L)", formParam.baseName, item)
                            .endControlFlow();
                    } else {
                        // `explode: false`: one field with the values joined by the delimiter of the style, the delimiter is written as is
                        var values = needsConverter(formParam)
                            ? CodeBlock.of("value.$N().stream().map(this.$N::convert).toList()", formParam.paramName, formParam.paramName + "Converter")
                            : CodeBlock.of("value.$N()", formParam.paramName);
                        apply.beginControlFlow("if (!value.$N().isEmpty())", formParam.paramName)
                            .addStatement("b.add($S, $S, $L)", formParam.baseName, delimiter, values)
                            .endControlFlow();
                    }
                } else if (requiresMapper(formParam)) {
                    apply.addStatement("b.add($S, $N.convert(value.$N()))", formParam.baseName, formParam.paramName + "Converter", formParam.paramName);
                } else {
                    apply.addStatement("b.add($S, $T.toString(value.$N()))", formParam.baseName, ClassName.get(Objects.class), formParam.paramName);
                }
                if (nullChecked) {
                    apply.endControlFlow();
                }
            }
            apply.addStatement("return b.write()");
        } else if (multipartForm) {
            apply.addStatement("var l = new $T<$T>()", ClassName.get(ArrayList.class), Classes.formPart);
            for (var formParam : operation.formParams) {
                // a required primitive record component is never null, so guarding it would not compile
                var nullChecked = !isRequiredPrimitive(formParam);
                if (nullChecked) {
                    apply.beginControlFlow("if (value.$N() != null)", formParam.paramName);
                }
                if (formParam.isFile) {
                    if (formParam.isArray) {
                        apply.addStatement("l.addAll(value.$N())", formParam.paramName);
                    } else {
                        apply.addStatement("l.add(value.$N())", formParam.paramName);
                    }
                } else if (isByteArrayArrayType(formParam)) {
                    apply.beginControlFlow("for (var item : value.$N())", formParam.paramName)
                        .addStatement("l.add($T.data($S, $T.getEncoder().encodeToString(item)))", Classes.formMultipart, formParam.baseName, ClassName.get(Base64.class))
                        .endControlFlow();
                } else if (isByteArrayType(formParam)) {
                    apply.addStatement("l.add($T.data($S, $T.getEncoder().encodeToString(value.$N())))", Classes.formMultipart, formParam.baseName, ClassName.get(Base64.class), formParam.paramName);
                } else if (isConvertibleArray(formParam)) {
                    // multiple values are sent as repeated same-named parts, one per element
                    apply.beginControlFlow("for (var item : value.$N())", formParam.paramName);
                    if (needsConverter(formParam)) {
                        apply.addStatement("l.add($L)", dataPart(formParam, CodeBlock.of("$N.convert(item)", formParam.paramName + "Converter")));
                    } else {
                        apply.addStatement("l.add($L)", dataPart(formParam, CodeBlock.of("item")));
                    }
                    apply.endControlFlow();
                } else if (requiresMapper(formParam)) {
                    apply.addStatement("l.add($L)", dataPart(formParam, CodeBlock.of("$N.convert(value.$N())", formParam.paramName + "Converter", formParam.paramName)));
                } else {
                    apply.addStatement("l.add($L)", dataPart(formParam, CodeBlock.of("$T.toString(value.$N())", ClassName.get(Objects.class), formParam.paramName)));
                }
                if (nullChecked) {
                    apply.endControlFlow();
                }
            }
            apply.addStatement("return $T.write(l)", Classes.multipartWriter);
        } else {
            throw new IllegalArgumentException(missingFormContentTypeError(operation));
        }

        return b.addMethod(constructor.build()).addMethod(apply.build()).build();
    }

    // a non-file, non-byte array whose elements are written as repeated same-named form fields
    private boolean isConvertibleArray(CodegenParameter p) {
        return Boolean.TRUE.equals(p.isArray) && !p.isFile && !isByteArrayArrayType(p);
    }

    private TypeName elementType(CodegenParameter p) {
        return ((ParameterizedTypeName) asType(p)).typeArguments().getFirst();
    }

    private boolean needsConverter(CodegenParameter p) {
        if (isConvertibleArray(p)) {
            // string elements are written directly; every other element type and a JSON string go through a converter
            return isJsonTypedFormPart(p) || !elementType(p).equals(ClassName.get(String.class));
        }
        return requiresMapper(p);
    }

    // a multipart part carries its media type, a part without one is plain text
    private CodeBlock dataPart(CodegenParameter p, CodeBlock value) {
        var contentType = formPartContentType(p);
        if (contentType == null) {
            return CodeBlock.of("$T.data($S, $L)", Classes.formMultipart, p.baseName, value);
        }
        return CodeBlock.of("$T.file($S, null, $S, $L.getBytes($T.UTF_8))", Classes.formMultipart, p.baseName, contentType, value, ClassName.get(java.nio.charset.StandardCharsets.class));
    }

    private boolean isRequiredPrimitive(CodegenParameter p) {
        // buildFormParamsRecord boxes optional components but keeps required primitives unboxed
        return p.required && !p.isFile && !p.isArray && asType(p).isPrimitive();
    }

    private boolean isByteArrayType(CodegenParameter p) {
        return "byte[]".equals(p.dataType) || "ByteArray".equals(p.dataType);
    }

    private boolean isByteArrayArrayType(CodegenParameter p) {
        return Boolean.TRUE.equals(p.isArray)
            && ("byte[]".equals(p.baseType)
                || "ByteArray".equals(p.baseType)
                || (p.dataType != null && (p.dataType.contains("byte[]") || p.dataType.contains("ByteArray"))));
    }

    private boolean requiresMapper(CodegenParameter p) {
        if (isContentJson(p) || isJsonTypedFormPart(p)) {
            return true;
        }
        if (p.isEnum || (p.allowableValues != null && !p.allowableValues.isEmpty())) {
            // an inline enum is generated as a plain String field, so it is written directly
            return !asType(p).equals(ClassName.get(String.class));
        }
        if (p.isFile) {
            return false;
        }
        return !p.isPrimitiveType;
    }

    private static String ambiguousFormContentTypeError(CodegenOperation operation) {
        return """
            Invalid OpenAPI operation `%s`: ambiguous form request body.

            Operation declares both `application/x-www-form-urlencoded` and `multipart/form-data`.
            Kora generates one form request mapper per operation and cannot choose between both encodings.

            Fix: keep exactly one supported form content type for this operation.
            """.formatted(operation.operationId);
    }

    private static String missingFormContentTypeError(CodegenOperation operation) {
        return """
            Invalid OpenAPI operation `%s`: unsupported form request body.

            Operation has form parameters, but consumes neither `application/x-www-form-urlencoded` nor `multipart/form-data`.

            Fix: set requestBody content type to one supported form media type, or remove form parameters.
            """.formatted(operation.operationId);
    }
}
