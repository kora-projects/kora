package ru.tinkoff.kora.json.annotation.processor.writer;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.json.annotation.processor.JsonTypes;
import ru.tinkoff.kora.json.annotation.processor.JsonUtils;

import javax.annotation.Nullable;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.io.IOException;

public class EnumWriterGenerator {

    public TypeSpec generateEnumWriter(TypeElement typeElement) {
        var typeName = ClassName.get(typeElement);
        var typeBuilder = TypeSpec.classBuilder(JsonUtils.jsonWriterName(typeElement))
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.koraGenerated)
                .addMember("value", CodeBlock.of("$S", JsonWriterGenerator.class.getCanonicalName()))
                .build())
            .addSuperinterface(ParameterizedTypeName.get(JsonTypes.jsonWriter, typeName))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addOriginatingElement(typeElement);
        var delegateType = ParameterizedTypeName.get(JsonTypes.enumJsonWriter, typeName);

        typeBuilder.addField(delegateType, "delegate", Modifier.PRIVATE, Modifier.FINAL);
        typeBuilder.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addCode("this.delegate = new $T<>($T.values(), v -> v.toString());\n", JsonTypes.enumJsonWriter, typeName)
            .build());
        typeBuilder.addMethod(MethodSpec.methodBuilder("write")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addException(IOException.class)
            .addParameter(JsonTypes.jsonGenerator, "_gen")
            .addParameter(ParameterSpec.builder(typeName, "_object").addAnnotation(Nullable.class).build())
            .addAnnotation(Override.class)
            .addCode("this.delegate.write(_gen, _object);\n")
            .build()
        );
        return typeBuilder.build();
    }
}
