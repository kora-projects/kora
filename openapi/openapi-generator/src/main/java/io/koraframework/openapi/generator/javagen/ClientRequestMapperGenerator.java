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
        for (var p : operation.formParams) {
            if (requiresMapper(p)) {
                var mapperType = ParameterizedTypeName.get(Classes.stringParameterConverter, asType(p));
                constructor.addParameter(mapperType, p.paramName + "Converter")
                    .addStatement("this.$N = $N", p.paramName + "Converter", p.paramName + "Converter");
                b.addField(mapperType, p.paramName + "Converter", Modifier.PRIVATE, Modifier.FINAL);
            }
        }
        var urlEncodedForm = operation.consumes != null && operation.consumes.stream()
            .map(m -> m.get("mediaType"))
            .anyMatch("application/x-www-form-urlencoded"::equalsIgnoreCase);
        var multipartForm = operation.consumes != null && operation.consumes.stream()
            .map(m -> m.get("mediaType"))
            .anyMatch("multipart/form-data"::equalsIgnoreCase);
        if (urlEncodedForm && multipartForm) {
            throw new IllegalArgumentException(ambiguousFormContentTypeError(operation));
        }
        if (urlEncodedForm) {
            apply.addStatement("var b = new $T()", URL_ENCODED_WRITER);
            for (var formParam : operation.formParams) {
                apply.beginControlFlow("if (value.$N() != null)", formParam.paramName);
                if (requiresMapper(formParam)) {
                    apply.addStatement("b.add($S, $N.convert(value.$N()))", formParam.baseName, formParam.paramName + "Converter", formParam.paramName);
                } else {
                    apply.addStatement("b.add($S, $T.toString(value.$N()))", formParam.baseName, ClassName.get(Objects.class), formParam.paramName);
                }
                apply.endControlFlow();
            }
            apply.addStatement("return b.write()");
        } else if (multipartForm) {
            apply.addStatement("var l = new $T<$T>()", ClassName.get(ArrayList.class), Classes.formPart);
            for (var formParam : operation.formParams) {
                apply.beginControlFlow("if (value.$N() != null)", formParam.paramName);
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
                } else if (requiresMapper(formParam)) {
                    apply.addStatement("l.add($T.data($S, $N.convert(value.$N())))", Classes.formMultipart, formParam.baseName, formParam.paramName + "Converter", formParam.paramName);
                } else {
                    apply.addStatement("l.add($T.data($S, $T.toString(value.$N())))", Classes.formMultipart, formParam.baseName, ClassName.get(Objects.class), formParam.paramName);
                }
                apply.endControlFlow();
            }
            apply.addStatement("return $T.write(l)", Classes.multipartWriter);
        } else {
            throw new IllegalArgumentException(missingFormContentTypeError(operation));
        }

        return b.addMethod(constructor.build()).addMethod(apply.build()).build();
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
        if (isContentJson(p)) {
            return true;
        }
        if (p.isEnum || (p.allowableValues != null && !p.allowableValues.isEmpty())) {
            return true;
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
