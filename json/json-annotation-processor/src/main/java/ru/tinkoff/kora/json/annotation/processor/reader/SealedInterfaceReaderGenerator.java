package ru.tinkoff.kora.json.annotation.processor.reader;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.SealedTypeUtils;
import ru.tinkoff.kora.json.annotation.processor.JsonTypes;
import ru.tinkoff.kora.json.annotation.processor.JsonUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.util.List;

import static ru.tinkoff.kora.annotation.processor.common.CommonUtils.decapitalize;

public class SealedInterfaceReaderGenerator {
    private final Types types;
    private final Elements elements;

    public SealedInterfaceReaderGenerator(ProcessingEnvironment processingEnvironment) {
        this.types = processingEnvironment.getTypeUtils();
        this.elements = processingEnvironment.getElementUtils();
    }

    public TypeSpec generateSealedReader(TypeElement jsonElement) {
        var typeName = JsonUtils.jsonReaderName(jsonElement);
        var typeBuilder = TypeSpec.classBuilder(typeName)
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.koraGenerated)
                .addMember("value", CodeBlock.of("$S", SealedInterfaceReaderGenerator.class.getCanonicalName()))
                .build())
            .addSuperinterface(ParameterizedTypeName.get(JsonTypes.jsonReader, ClassName.get(jsonElement)))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addOriginatingElement(jsonElement);

        for (var typeParameter : jsonElement.getTypeParameters()) {
            typeBuilder.addTypeVariable(TypeVariableName.get(typeParameter));
        }
        var permittedSubclasses = SealedTypeUtils.collectFinalPermittedSubtypes(this.types, this.elements, jsonElement);

        this.addReaders(typeBuilder, permittedSubclasses);

        var discriminatorField = JsonUtils.discriminatorField(this.types, jsonElement);
        if (discriminatorField == null) {
            discriminatorField = "@type";
        }
        var method = MethodSpec.methodBuilder("read")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addException(IOException.class)
            .addParameter(JsonTypes.jsonParser, "_parser")
            .returns(ClassName.get(jsonElement))
            .addAnnotation(Override.class)
            .addAnnotation(Nullable.class);
        method.addCode("var bufferingParser = new $T(_parser);\n", JsonTypes.bufferingJsonParser);
        method.addCode("var discriminator = $T.readStringDiscriminator(bufferingParser, $S);\n", JsonTypes.discriminatorHelper, discriminatorField);
        method.addCode("if (discriminator == null) throw new $T(_parser, $S);\n", JsonTypes.jsonParseException, "Discriminator required, but not provided");
        method.addCode("var bufferedParser = $T.createFlattened(false, bufferingParser.reset(), _parser);\n", JsonTypes.jsonParserSequence);
        method.addCode("bufferedParser.nextToken();\n");
        method.addCode("return switch(discriminator) {$>\n");
        for (var elem : permittedSubclasses) {
            var readerName = getReaderFieldName(elem);
            var discriminatorValues = JsonUtils.discriminatorValue(elem);
            for (var discriminatorValue : discriminatorValues) {
                method.addCode("case $S -> $L.read(bufferedParser);\n", discriminatorValue, readerName);
            }
        }
        method.addCode("default -> throw new $T(_parser, $S + discriminator + \"'\");$<\n};", JsonTypes.jsonParseException, "Unknown discriminator: '");
        typeBuilder.addMethod(method.build());

        return typeBuilder.build();
    }

    private void addReaders(TypeSpec.Builder typeBuilder, List<? extends Element> jsonElements) {
        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);
        jsonElements.forEach(elem -> {
            var fieldName = getReaderFieldName(elem);
            var fieldType = ParameterizedTypeName.get(JsonTypes.jsonReader, TypeName.get(elem.asType()));
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
