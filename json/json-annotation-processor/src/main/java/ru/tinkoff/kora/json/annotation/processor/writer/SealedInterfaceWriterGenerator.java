package ru.tinkoff.kora.json.annotation.processor.writer;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.json.annotation.processor.JsonTypes;
import ru.tinkoff.kora.json.annotation.processor.JsonUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.util.List;

import static ru.tinkoff.kora.annotation.processor.common.CommonUtils.decapitalize;

public class SealedInterfaceWriterGenerator {
    private final Types types;
    private final Elements elements;

    public SealedInterfaceWriterGenerator(ProcessingEnvironment processingEnvironment) {
        this.types = processingEnvironment.getTypeUtils();
        this.elements = processingEnvironment.getElementUtils();
    }

    public TypeSpec generateSealedWriter(TypeElement jsonElement, List<? extends Element> jsonElements) {
        var typeBuilder = TypeSpec.classBuilder(JsonUtils.jsonWriterName(jsonElement))
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.koraGenerated)
                .addMember("value", CodeBlock.of("$S", SealedInterfaceWriterGenerator.class.getCanonicalName()))
                .build())
            .addSuperinterface(ParameterizedTypeName.get(JsonTypes.jsonWriter, TypeName.get(jsonElement.asType())))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addOriginatingElement(jsonElement);
        this.addWriters(typeBuilder, jsonElements);


        for (TypeParameterElement typeParameter : jsonElement.getTypeParameters()) {
            typeBuilder.addTypeVariable(TypeVariableName.get(typeParameter));
        }


        var method = MethodSpec.methodBuilder("write")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addException(IOException.class)
            .addParameter(JsonTypes.jsonGenerator, "_gen")
            .addParameter(ParameterSpec.builder(TypeName.get(jsonElement.asType()), "_object").addAnnotation(Nullable.class).build())
            .addAnnotation(Override.class);
        method.beginControlFlow("if (_object == null)")
            .addStatement("_gen.writeNull()");
        for (var elem : jsonElements) {
            var writerName = getWriterFieldName(elem);
            var elemErasure = types.erasure(elem.asType());
            method.nextControlFlow("else if (_object instanceof $T _o)", elemErasure)
                .addStatement("$L.write(_gen, _o)", writerName);
        }
        method.nextControlFlow("else")
            .addStatement("throw new $T($S)", IllegalStateException.class, "Unsupported class")
            .endControlFlow();
        typeBuilder.addMethod(method.build());
        return typeBuilder.build();
    }

    private void addWriters(TypeSpec.Builder typeBuilder, List<? extends Element> jsonElements) {
        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);
        jsonElements.forEach(elem -> {
            var fieldName = getWriterFieldName(elem);
            var fieldType = ParameterizedTypeName.get(JsonTypes.jsonWriter, TypeName.get(elem.asType()));
            var readerField = FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE, Modifier.FINAL);
            constructor.addParameter(fieldType, fieldName);
            constructor.addStatement("this.$L = $L", fieldName, fieldName);
            typeBuilder.addField(readerField.build());
        });
        typeBuilder.addMethod(constructor.build());
    }

    @Nonnull
    private String getWriterFieldName(Element elem) {
        return decapitalize(elem.getSimpleName().toString() + "Reader");
    }
}
