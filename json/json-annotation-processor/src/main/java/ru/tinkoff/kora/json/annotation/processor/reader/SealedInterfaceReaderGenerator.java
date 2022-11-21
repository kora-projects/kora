package ru.tinkoff.kora.json.annotation.processor.reader;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.squareup.javapoet.*;
import ru.tinkoff.kora.json.annotation.processor.JsonUtils;
import ru.tinkoff.kora.json.annotation.processor.KnownType;
import ru.tinkoff.kora.json.common.BufferedParserWithDiscriminator;
import ru.tinkoff.kora.json.common.JsonReader;
import ru.tinkoff.kora.json.common.annotation.JsonDiscriminatorValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.processing.Generated;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static ru.tinkoff.kora.annotation.processor.common.CommonUtils.decapitalize;

public class SealedInterfaceReaderGenerator {
    private final Types types;
    private final Elements elements;
    private final ReaderTypeMetaParser readerTypeMetaParser;
    private final TypeElement readerErasure;

    public SealedInterfaceReaderGenerator(ProcessingEnvironment processingEnvironment) {
        this.types = processingEnvironment.getTypeUtils();
        this.elements = processingEnvironment.getElementUtils();
        this.readerTypeMetaParser = new ReaderTypeMetaParser(processingEnvironment, new KnownType(processingEnvironment.getElementUtils(), processingEnvironment.getTypeUtils()));
        this.readerErasure = (TypeElement) this.types.asElement(
            this.types.erasure(this.elements.getTypeElement(JsonReader.class.getCanonicalName()).asType())
        );
    }

    public TypeSpec generateSealedReader(TypeElement jsonElement, List<? extends Element> jsonElements) {
        var meta = Objects.requireNonNull(this.readerTypeMetaParser.parse(jsonElement.asType()));

        var readerInterface = this.types.getDeclaredType(this.readerErasure, jsonElement.asType());
        var typeName = JsonUtils.jsonReaderName(jsonElement);
        var typeBuilder = TypeSpec.classBuilder(typeName)
            .addAnnotation(AnnotationSpec.builder(Generated.class)
                .addMember("value", CodeBlock.of("$S", SealedInterfaceReaderGenerator.class.getCanonicalName()))
                .build())
            .addSuperinterface(readerInterface)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addOriginatingElement(jsonElement);


        for (TypeParameterElement typeParameter : jsonElement.getTypeParameters()) {
            typeBuilder.addTypeVariable(TypeVariableName.get(typeParameter));
        }


        this.addReaders(typeBuilder, jsonElements);

        var discriminatorField = meta.discriminatorField();

        var method = MethodSpec.methodBuilder("read")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addException(IOException.class)
            .addParameter(TypeName.get(JsonParser.class), "_parser")
            .returns(ClassName.get(jsonElement))
            .addAnnotation(Override.class)
            .addAnnotation(Nullable.class);
        method.addCode("var bufferedParser = new $T(_parser);\n", BufferedParserWithDiscriminator.class);
        method.addCode("var discriminator = bufferedParser.getDiscriminator($S);\n", discriminatorField);
        method.addCode("if (discriminator == null) throw new $T(_parser, $S);\n", JsonParseException.class, "Discriminator required, but not provided");
        method.addCode("bufferedParser.resetPosition();\n");
        method.addCode("return switch(discriminator) {$>\n");
        jsonElements.forEach(elem -> {
            var readerName = getReaderFieldName(elem);
            var discriminatorValueAnnotation = elem.getAnnotation(JsonDiscriminatorValue.class);
            var requiredDiscriminatorValue = discriminatorValueAnnotation == null ? ((TypeElement) elem).getSimpleName().toString() : discriminatorValueAnnotation.value();
            method.addCode("case $S -> $L.read(bufferedParser);\n", requiredDiscriminatorValue, readerName);
        });
        method.addCode("default -> throw new $T(_parser, $S);$<\n};", JsonParseException.class, "Unknown discriminator");
        typeBuilder.addMethod(method.build());

        return typeBuilder.build();
    }

    private void addReaders(TypeSpec.Builder typeBuilder, List<? extends Element> jsonElements) {
        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);
        jsonElements.forEach(elem -> {
            var fieldName = getReaderFieldName(elem);
            var fieldType = ParameterizedTypeName.get(ClassName.get(JsonReader.class), TypeName.get(elem.asType()));
            var readerField = FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE, Modifier.FINAL);
            constructor.addParameter(fieldType, fieldName);
            constructor.addStatement("this.$L = $L", fieldName, fieldName);
            typeBuilder.addField(readerField.build());
        });
        typeBuilder.addMethod(constructor.build());
    }

    @Nonnull
    private String getReaderFieldName(Element elem) {
        return decapitalize(elem.getSimpleName().toString() + "Reader");
    }
}
