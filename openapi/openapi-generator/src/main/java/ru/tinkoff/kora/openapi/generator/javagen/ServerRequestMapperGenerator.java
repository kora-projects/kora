package ru.tinkoff.kora.openapi.generator.javagen;

import com.palantir.javapoet.*;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.model.OperationsMap;
import ru.tinkoff.kora.openapi.generator.KoraCodegen;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ServerRequestMapperGenerator extends AbstractJavaGenerator<OperationsMap> {
    @Override
    public JavaFile generate(OperationsMap ctx) {
        var b = TypeSpec.interfaceBuilder(ctx.get("classname") + "ServerRequestMappers")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(generated())
            .addAnnotation(Classes.module);

        for (var operation : ctx.getOperations().getOperation()) {
            if (operation.getHasFormParams()) {
                b.addType(buildFormParamsRequestMapper(ctx, operation));
            }
        }

        return JavaFile.builder(apiPackage, b.build()).build();
    }

    private TypeSpec buildFormParamsRequestMapper(OperationsMap ctx, CodegenOperation op) {
        var formParamClass = ClassName.get(apiPackage, ctx.get("classname") + "Controller", capitalize(op.operationId) + "FormParam");
        var b = TypeSpec.classBuilder(capitalize(op.operationId) + "FormParamRequestMapper")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addAnnotation(generated())
            .addSuperinterface(ParameterizedTypeName.get(Classes.httpServerRequestMapper, formParamClass));
        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);
        var multipartForm = op.consumes != null && op.consumes.stream()
            .map(m -> m.get("mediaType"))
            .anyMatch("multipart/form-data"::equalsIgnoreCase);
        var urlEncodedForm = op.consumes != null && op.consumes.stream()
            .map(m -> m.get("mediaType"))
            .anyMatch("application/x-www-form-urlencoded"::equalsIgnoreCase);

        for (var formParam : op.formParams) {
            var paramType = asType(formParam);
            if (paramType.equals(ParameterizedTypeName.get(List.class, String.class)) || paramType.equals(ClassName.get(String.class))) {
                continue;
            }
            var mapperType = ParameterizedTypeName.get(Classes.stringParameterReader, formParam.isArray ? ((ParameterizedTypeName) paramType).typeArguments().getFirst().box() : paramType.box());
            var converterName = formParam.paramName + "Converter";
            b.addField(mapperType, converterName, Modifier.PRIVATE, Modifier.FINAL);
            var param = ParameterSpec.builder(mapperType, converterName);
            if (KoraCodegen.isContentJson(formParam)) {
                param.addAnnotation(Classes.json);
            }
            constructor.addParameter(param.build());
            constructor.addStatement("this.$N = $N", converterName, converterName);
        }
        b.addMethod(constructor.build());

        var apply = MethodSpec.methodBuilder("apply")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .returns(formParamClass)
            .addException(IOException.class)
            .addParameter(Classes.httpServerRequest, "rq");

        if (urlEncodedForm) {
            apply.addCode(mapUrlEncoded(ctx, op, formParamClass));
        } else if (multipartForm) {
            apply.addCode(mapMultipart(ctx, op, formParamClass));
        } else {
            throw new IllegalArgumentException();
        }


        b.addMethod(apply.build());
        return b.build();
    }

    private CodeBlock mapMultipart(OperationsMap ctx, CodegenOperation op, ClassName formParamClass) {
        var b = CodeBlock.builder();
        for (var formParam : op.formParams) {
            var type = asType(formParam);
            if (formParam.isFile) {
                type = Classes.formPart;
            }
            b.addStatement("var $N = ($T) null", formParam.paramName, type);
        }
        b.addStatement("var _parts = $T.read(rq)", ClassName.get("ru.tinkoff.kora.http.server.common.form", "MultipartReader"));
        b.beginControlFlow("for (var _part : _parts) switch(_part.name())");
        for (var formParam : op.formParams) {
            b.beginControlFlow("case $S -> ", formParam.baseName);
            var type = asType(formParam);
            if (formParam.isFile) {
                b.addStatement("$N = _part", formParam.paramName);
            } else if (type.equals(ClassName.get(String.class))) {
                b.addStatement("$N = new $T(_part.content(), $T.UTF_8)", formParam.paramName, String.class, StandardCharsets.class);
            } else {
                var converterName = formParam.paramName + "Converter";
                b.addStatement("$N = $T.read(new $T(_part.content(), $T.UTF_8))", formParam.paramName, converterName, String.class, StandardCharsets.class);
            }
            b.endControlFlow();
        }
        b.add("default -> {}\n");
        b.endControlFlow();
        for (var formParam : op.formParams) {
            if (formParam.required) {
                b.beginControlFlow("if ($N == null)", formParam.paramName);
                b.addStatement("throw $T.of(400, $S)", Classes.httpServerResponseException, "Form key '%s' is required".formatted(formParam.baseName));
                b.endControlFlow();
            }
        }

        b.add("return new $T(", formParamClass);
        for (int i = 0; i < op.formParams.size(); i++) {
            var formParam = op.formParams.get(i);
            if (i > 0) {
                b.add(", ");
            }
            b.add(formParam.paramName);

        }
        b.add(");\n");
        return b.build();
    }

    private CodeBlock mapUrlEncoded(OperationsMap ctx, CodegenOperation op, ClassName formParamClass) {
        var b = CodeBlock.builder();
        b.beginControlFlow("try (var _body = rq.body(); var _is = _body.asInputStream())");
        b.addStatement("var _bytes = _is.readAllBytes()");
        b.addStatement("var _bodyString = new $T(_bytes, $T.UTF_8)", String.class, StandardCharsets.class);
        b.addStatement("var _formData = $T.read(_bodyString);", ClassName.get("ru.tinkoff.kora.http.server.common.form", "FormUrlEncodedServerRequestMapper"));
        for (var p : op.formParams) {
            var type = asType(p);
            var partName = "_" + p.paramName + "_part";
            if (p.isArray) {
                var ptn = (ParameterizedTypeName) type;
                b.addStatement("var $N = _formData.get($S)", partName, p.baseName);
                if (p.required) {
                    b.beginControlFlow("if ($N == null)", partName)
                        .addStatement("throw $T.of(400, $S)", Classes.httpServerResponseException, "Form key '%s' is required".formatted(p.baseName))
                        .endControlFlow();
                }
                if (ptn.typeArguments().getFirst().equals(ClassName.get(String.class))) {
                    b.addStatement("var $N = $N.values()", p.paramName, partName);
                } else {
                    var converterName = p.paramName + "Converter";
                    b.addStatement("var $N = $N.values().stream().map(this.$N::read).toList()", p.paramName, partName, converterName);
                }
                continue;
            }
            b.addStatement("var $N = _formData.get($S)", partName, p.baseName);
            var strName = type.equals(ClassName.get(String.class)) ? p.paramName : "_" + p.paramName + "_str";
            b.addStatement("var $N = $N != null && !$N.values().isEmpty() ? $N.values().getFirst() : null", strName, partName, partName, partName);
            if (p.required) {
                b.beginControlFlow("if ($N == null)", strName)
                    .addStatement("throw $T.of(400, $S)", Classes.httpServerResponseException, "Form key '%s' is required".formatted(p.baseName))
                    .endControlFlow();
            }
            if (!type.equals(ClassName.get(String.class))) {
                var converterName = p.paramName + "Converter";
                b.addStatement("var $N = $N.read($N)", p.paramName, converterName, strName);
                if (p.required) {
                    b.beginControlFlow("if ($N == null)", p.paramName)
                        .addStatement("throw $T.of(400, $S)", Classes.httpServerResponseException, "Form key '%s' is required".formatted(p.baseName))
                        .endControlFlow();
                }
            }
        }
        b.add("return new $T(", formParamClass);
        for (int i = 0; i < op.formParams.size(); i++) {
            if (i > 0) {
                b.add(", ");
            }
            b.add(op.formParams.get(i).paramName);
        }
        b.add(");\n");
        b.endControlFlow();
        return b.build();
    }
}
