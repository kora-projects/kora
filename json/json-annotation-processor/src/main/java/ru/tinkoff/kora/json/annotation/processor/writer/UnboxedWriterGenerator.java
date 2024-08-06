package ru.tinkoff.kora.json.annotation.processor.writer;

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
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.json.annotation.processor.JsonTypes;
import ru.tinkoff.kora.json.annotation.processor.JsonUtils;

public class UnboxedWriterGenerator {

    @Nullable
    public TypeSpec generate(UnboxedWriterMeta meta) {
        var typeBuilder = TypeSpec.classBuilder(JsonUtils.jsonWriterName(meta.typeElement()))
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.koraGenerated)
                .addMember("value", CodeBlock.of("$S", UnboxedWriterGenerator.class.getCanonicalName()))
                .build())
            .addSuperinterface(ParameterizedTypeName.get(JsonTypes.jsonWriter, TypeName.get(meta.typeMirror())))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addOriginatingElement(meta.typeElement());

        for (var typeParameter : meta.typeElement().getTypeParameters()) {
            typeBuilder.addTypeVariable(TypeVariableName.get(typeParameter));
        }

        var field = meta.field();

        var fieldName = this.writerFieldName(field);
        var fieldType = ParameterizedTypeName.get(JsonTypes.jsonWriter, TypeName.get(field.typeMirror()));

        var writerField = FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE, Modifier.FINAL).build();
        var writerParameter = ParameterSpec.builder(fieldType, fieldName).build();

        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(writerParameter)
            .addStatement("this.$N = $N", writerField, writerParameter);

        typeBuilder.addField(writerField);
        typeBuilder.addMethod(constructor.build());

        var method = MethodSpec.methodBuilder("write")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addException(IOException.class)
            .addParameter(JsonTypes.jsonGenerator, "_gen")
            .addParameter(ParameterSpec.builder(TypeName.get(meta.typeMirror()), "_object").addAnnotation(Nullable.class).build())
            .addAnnotation(Override.class)
            .addCode("if (_object == null) {$>\n_gen.writeNull();\nreturn;$<\n}\n");

        method.addStatement("$N.write(_gen, _object.$L)", writerField, field.accessor());

        typeBuilder.addMethod(method.build());
        return typeBuilder.build();
    }

    private String writerFieldName(UnboxedWriterMeta.FieldMeta field) {
        return field.accessor().getSimpleName() + "Writer";
    }
}
