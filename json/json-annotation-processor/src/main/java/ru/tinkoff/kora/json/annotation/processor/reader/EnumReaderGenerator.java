package ru.tinkoff.kora.json.annotation.processor.reader;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.json.annotation.processor.JsonTypes;
import ru.tinkoff.kora.json.annotation.processor.JsonUtils;

import javax.annotation.Nullable;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.io.IOException;

public class EnumReaderGenerator {

    public TypeSpec generateForEnum(TypeElement typeElement) {
        var typeName = ClassName.get(typeElement);

        var typeBuilder = TypeSpec.classBuilder(JsonUtils.jsonReaderName(typeElement))
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.koraGenerated)
                .addMember("value", CodeBlock.of("$S", JsonReaderGenerator.class.getCanonicalName()))
                .build())
            .addSuperinterface(ParameterizedTypeName.get(JsonTypes.jsonReader, typeName))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addOriginatingElement(typeElement);
        var delegateType = ParameterizedTypeName.get(JsonTypes.enumJsonReader, typeName);

        typeBuilder.addField(delegateType, "delegate", Modifier.PRIVATE, Modifier.FINAL);
        typeBuilder.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            // todo detect string representation method for enum
            .addCode("this.delegate = new $T<>($T.values(), v -> v.toString());\n", JsonTypes.enumJsonReader, typeName)
            .build());
        typeBuilder.addMethod(MethodSpec.methodBuilder("read")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addException(IOException.class)
            .addParameter(JsonTypes.jsonParser, "_parser")
            .returns(typeName)
            .addAnnotation(Override.class)
            .addAnnotation(Nullable.class)
            .addCode("return this.delegate.read(_parser);\n")
            .build()
        );
        return typeBuilder.build();
    }
}
