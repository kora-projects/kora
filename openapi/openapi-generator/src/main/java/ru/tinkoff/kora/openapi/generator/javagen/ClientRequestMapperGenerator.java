package ru.tinkoff.kora.openapi.generator.javagen;

import com.palantir.javapoet.*;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.model.OperationsMap;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.Objects;

import static ru.tinkoff.kora.openapi.generator.KoraCodegen.isContentJson;

public class ClientRequestMapperGenerator extends AbstractJavaGenerator<OperationsMap> {
    @Override
    public JavaFile generate(OperationsMap ctx) {
        var className = ClassName.get(apiPackage, ctx.get("classname") + "ClientRequestMappers");
        var b = TypeSpec.interfaceBuilder(className)
            .addAnnotation(generated());
        for (var operation : ctx.getOperations().getOperation()) {
            if (!operation.getHasFormParams()) {
                continue;
            }
            b.addType(buildFormMapper(ctx, className, operation));
        }

        return JavaFile.builder(apiPackage, b.build()).build();
    }

    private TypeSpec buildFormMapper(OperationsMap ctx, ClassName rootName, CodegenOperation operation) {
        var className = rootName.nestedClass(capitalize(operation.operationId) + "FormParamRequestMapper");
        var formParamClassName = ClassName.get(apiPackage, ctx.get("classname").toString(), capitalize(operation.operationId) + "FormParam");
        var b = TypeSpec.classBuilder(className)
            .addAnnotation(generated())
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .addSuperinterface(ParameterizedTypeName.get(Classes.httpClientRequestMapper, formParamClassName));
        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);
        var apply = MethodSpec.methodBuilder("apply")
            .returns(Classes.httpBodyOutput)
            .addParameter(Classes.context, "ctx")
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
            throw new IllegalArgumentException("Unsupported form type: " + operation);
        }
        if (urlEncodedForm) {
            apply.addStatement("var b = new $T()", ClassName.get("ru.tinkoff.kora.http.client.common.form", "UrlEncodedWriter"));
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
                    apply.addStatement("l.add(value.$N())", formParam.paramName);
                } else if (requiresMapper(formParam)) {
                    apply.addStatement("l.add($T.data($S, $N.convert(value.$N())))", Classes.formMultipart, formParam.baseName, formParam.paramName + "Converter", formParam.paramName);
                } else {
                    apply.addStatement("l.add($T.data($S, $T.toString(value.$N())))", Classes.formMultipart, formParam.baseName, ClassName.get(Objects.class), formParam.paramName);
                }
                apply.endControlFlow();
            }
            apply.addStatement("return $T.write(l)", Classes.multipartWriter);
        } else {
            throw new IllegalStateException();
        }

        return b.addMethod(constructor.build()).addMethod(apply.build()).build();
    }

    private boolean requiresMapper(CodegenParameter p) {
        if (isContentJson(p)) {
            return true;
        }
        if (p.isEnum || (p.allowableValues != null && !p.allowableValues.isEmpty())) {
            return true;
        }
        return !p.isPrimitiveType;
    }
}
