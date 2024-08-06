package ru.tinkoff.kora.json.annotation.processor.reader;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import jakarta.annotation.Nullable;
import java.io.IOException;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeParameterElement;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.json.annotation.processor.JsonTypes;
import ru.tinkoff.kora.json.annotation.processor.JsonUtils;

public class UnboxedReaderGenerator {

    public TypeSpec generateForUnboxed(UnboxedReaderMeta meta) {

        var typeBuilder = TypeSpec.classBuilder(JsonUtils.jsonReaderName(meta.typeElement()))
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.koraGenerated)
                .addMember("value", CodeBlock.of("$S", UnboxedReaderGenerator.class.getCanonicalName()))
                .build())
            .addSuperinterface(ParameterizedTypeName.get(JsonTypes.jsonReader, TypeName.get(meta.typeMirror())))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addOriginatingElement(meta.typeElement());

        for (TypeParameterElement typeParameter : meta.typeElement().getTypeParameters()) {
            typeBuilder.addTypeVariable(TypeVariableName.get(typeParameter));
        }

        var field = meta.field();

        var fieldName = this.readerFieldName(field);
        var fieldType = ParameterizedTypeName.get(JsonTypes.jsonReader, field.typeName());
        var readerField = FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE, Modifier.FINAL).build();
        var readerParameter = ParameterSpec.builder(fieldType, fieldName).build();

        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(readerParameter)
            .addStatement("this.$N = $N", readerField, readerParameter);

        typeBuilder.addField(readerField);
        typeBuilder.addMethod(constructor.build());

        var method = MethodSpec.methodBuilder("read")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addException(IOException.class)
            .addParameter(JsonTypes.jsonParser, "_parser")
            .returns(TypeName.get(meta.typeMirror()))
            .addAnnotation(Override.class)
            .addAnnotation(Nullable.class);

        method.addStatement("var _token = _parser.currentToken()");
        method.beginControlFlow("if (_token == $T.VALUE_NULL)", JsonTypes.jsonToken);

        if (isNullable(field)) {
            method.addStatement("return new $T(null)", meta.typeElement());
        } else {
            method.addStatement(
                "throw new $T(_parser, $S)",
                JsonTypes.jsonParseException,
                "Expecting nonnull value, got VALUE_NULL token"
            );
        }

        method.endControlFlow();

        method.addStatement("return new $T($N.read(_parser))",  meta.typeElement(), readerField);

        typeBuilder.addMethod(method.build());

        return typeBuilder.build();
    }

    private String readerFieldName(UnboxedReaderMeta.FieldMeta field) {
        return field.parameter().getSimpleName() + "Reader";
    }

    private boolean isNullable(UnboxedReaderMeta.FieldMeta field) {
        if (field.parameter().asType().getKind().isPrimitive()) {
            return false;
        }

        return CommonUtils.isNullable(field.parameter());
    }
}
