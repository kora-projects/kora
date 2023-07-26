package ru.tinkoff.kora.json.annotation.processor.writer;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.json.annotation.processor.JsonTypes;
import ru.tinkoff.kora.json.annotation.processor.JsonUtils;

import javax.annotation.Nullable;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
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
        var enumValue = this.detectValueType(typeElement);
        var delegateType = ParameterizedTypeName.get(JsonTypes.enumJsonWriter, typeName, enumValue.type.box());

        typeBuilder.addField(delegateType, "delegate", Modifier.PRIVATE, Modifier.FINAL);
        typeBuilder.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ParameterizedTypeName.get(JsonTypes.jsonWriter, enumValue.type.box()), "valueWriter")
            .addCode("this.delegate = new $T<>($T.values(), $T::$N, valueWriter);\n", JsonTypes.enumJsonWriter, typeName, typeName, enumValue.accessor)
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

    record EnumValue(TypeName type, String accessor) {}

    private EnumValue detectValueType(TypeElement typeElement) {
        for (var enclosedElement : typeElement.getEnclosedElements()) {
            if (!enclosedElement.getModifiers().contains(Modifier.PUBLIC)) continue;
            if (enclosedElement.getModifiers().contains(Modifier.STATIC)) continue;
            if (enclosedElement.getKind() != ElementKind.METHOD) continue;
            if (enclosedElement instanceof ExecutableElement executableElement && executableElement.getParameters().isEmpty()) {
                if (AnnotationUtils.isAnnotationPresent(executableElement, JsonTypes.json)) {
                    var typeName = TypeName.get(executableElement.getReturnType());
                    return new EnumValue(typeName, executableElement.getSimpleName().toString());
                }
            }
        }
        var typeName = ClassName.get(String.class);
        return new EnumValue(typeName, "toString");
    }
}
