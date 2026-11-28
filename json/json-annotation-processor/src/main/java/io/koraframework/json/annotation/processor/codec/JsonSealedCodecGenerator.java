package io.koraframework.json.annotation.processor.codec;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.*;
import io.koraframework.json.annotation.processor.*;
import io.koraframework.json.annotation.processor.reader.*;

import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.util.*;
import java.util.*;

import static io.koraframework.annotation.processor.common.CommonUtils.*;

public class JsonSealedCodecGenerator {
    private final Types types;
    private final Elements elements;

    public JsonSealedCodecGenerator(ProcessingEnvironment processingEnvironment) {
        this.types = processingEnvironment.getTypeUtils();
        this.elements = processingEnvironment.getElementUtils();
    }

    public TypeSpec generateSealedCodec(TypeElement jsonElement) {
        var targetType = ClassName.get(jsonElement);
        String codecClassName = JsonUtils.jsonReaderName(jsonElement).replace("JsonReader", "JsonCodec");

        var typeBuilder = TypeSpec.classBuilder(codecClassName)
            .addAnnotation(AnnotationUtils.generated(SealedInterfaceReaderGenerator.class))
            .addSuperinterface(ParameterizedTypeName.get(JsonTypes.jsonReader, targetType))
            .addSuperinterface(ParameterizedTypeName.get(JsonTypes.jsonWriter, targetType))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addOriginatingElement(jsonElement);

        for (var typeParameter : jsonElement.getTypeParameters()) {
            typeBuilder.addTypeVariable(TypeVariableName.get(typeParameter));
        }

        var permittedSubclasses = SealedTypeUtils.collectFinalPermittedSubtypes(this.types, this.elements, jsonElement);

        this.addCombinedDependencies(typeBuilder, permittedSubclasses);
        this.addReadMethod(typeBuilder, jsonElement, permittedSubclasses);
        this.addWriteMethod(typeBuilder, jsonElement, permittedSubclasses);

        typeBuilder.addMethod(MethodSpec.methodBuilder("__jsonPath")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .addParameter(JsonTypes.jsonParser, "__parser")
            .returns(String.class)
            .addStatement("var __p = __parser.streamReadContext().pathAsPointer().toString()")
            .addStatement("return __p.isEmpty() ? $S : __p", "<root>")
            .build());

        return typeBuilder.build();
    }

    private void addCombinedDependencies(TypeSpec.Builder typeBuilder, List<TypeElement> jsonElements) {
        var constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);

        jsonElements.forEach(elem -> {
            var readerFieldName = decapitalize(elem.getSimpleName() + "Reader");
            var readerFieldType = ParameterizedTypeName.get(JsonTypes.jsonReader, TypeName.get(elem.asType()));
            var readerField = FieldSpec.builder(readerFieldType, readerFieldName, Modifier.PRIVATE, Modifier.FINAL);

            constructor.addParameter(readerFieldType, readerFieldName);
            constructor.addStatement("this.$L = $L", readerFieldName, readerFieldName);
            typeBuilder.addField(readerField.build());

            var writerFieldName = decapitalize(elem.getSimpleName() + "Writer");
            var writerFieldType = ParameterizedTypeName.get(JsonTypes.jsonWriter, TypeName.get(elem.asType()));
            var writerField = FieldSpec.builder(writerFieldType, writerFieldName, Modifier.PRIVATE, Modifier.FINAL);

            constructor.addParameter(writerFieldType, writerFieldName);
            constructor.addStatement("this.$L = $L", writerFieldName, writerFieldName);
            typeBuilder.addField(writerField.build());
        });

        typeBuilder.addMethod(constructor.build());
    }

    private void addReadMethod(TypeSpec.Builder typeBuilder, TypeElement jsonElement, List<TypeElement> permittedSubclasses) {
        var discriminator = JsonUtils.discriminator(this.types, jsonElement);
        var discriminatorField = discriminator == null ? "@type" : discriminator.field();
        var defaultDiscriminatorValue = discriminator == null ? null : discriminator.defaultValue();
        var typeSimpleName = jsonElement.getSimpleName().toString();
        var allValues = permittedSubclasses.stream().flatMap(e -> JsonUtils.discriminatorValue(e).stream()).toList();

        var method = MethodSpec.methodBuilder("read")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(JsonTypes.jsonParser, "__parser")
            .returns(ClassName.get(jsonElement).annotated(CommonClassNames.nullableAnnotation))
            .addAnnotation(Override.class);

        method.beginControlFlow("if (__parser.currentToken() == $T.VALUE_NULL)", JsonTypes.jsonToken)
            .addStatement("return null")
            .endControlFlow();

        method.addStatement("var bufferingParser = new $T(__parser)", JsonTypes.bufferingJsonParser);
        method.addStatement("var discriminator = $T.readStringDiscriminator(bufferingParser, $S)", JsonTypes.discriminatorHelper, discriminatorField);

        if (defaultDiscriminatorValue == null || defaultDiscriminatorValue.isEmpty()) {
            method.addCode("if (discriminator == null) throw new $T(__parser, $S + __jsonPath(__parser) + $S);\n",
                JsonTypes.jsonParseException,
                "Failed to read json " + typeSimpleName + ": missing required discriminator field \"" + discriminatorField + "\", expected one of " + allValues + " (at ",
                ")");
        } else {
            method.addStatement("if (discriminator == null) discriminator = $S", defaultDiscriminatorValue);
        }

        method.addStatement("var bufferedParser = $T.createFlattened(false, bufferingParser.reset(), __parser)", JsonTypes.jsonParserSequence);
        method.addStatement("bufferedParser.nextToken()");

        boolean first = true;
        for (var elem : permittedSubclasses) {
            var readerName = decapitalize(elem.getSimpleName() + "Reader");
            var discriminatorValues = JsonUtils.discriminatorValue(elem);
            for (var discriminatorValue : discriminatorValues) {
                if (first) {
                    method.beginControlFlow("if ($S.equals(discriminator))", discriminatorValue);
                    first = false;
                } else {
                    method.nextControlFlow("else if ($S.equals(discriminator))", discriminatorValue);
                }
                method.addStatement("return $L.read(bufferedParser)", readerName);
            }
        }

        if (!first) {
            method.nextControlFlow("else");
        } else {
            method.beginControlFlow("if (true)");
        }

        method.addCode("throw new $T(__parser, $S + discriminator + $S + __jsonPath(__parser) + $S);\n",
            JsonTypes.jsonParseException,
            "Failed to read json " + typeSimpleName + ": unknown discriminator value \"",
            "\" for field \"" + discriminatorField + "\", expected one of " + allValues + " (at ",
            ")");
        method.endControlFlow();

        typeBuilder.addMethod(method.build());
    }

    private void addWriteMethod(TypeSpec.Builder typeBuilder, TypeElement jsonElement, List<? extends Element> jsonElements) {
        var method = MethodSpec.methodBuilder("write")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(JsonTypes.jsonGenerator, "_gen")
            .addParameter(ParameterSpec.builder(TypeName.get(jsonElement.asType()).annotated(CommonClassNames.nullableAnnotation), "_object").build())
            .addAnnotation(Override.class);

        method.beginControlFlow("if (_object == null)")
            .addStatement("_gen.writeNull()");

        for (var elem : jsonElements) {
            var writerName = decapitalize(elem.getSimpleName() + "Writer");
            var elemErasure = types.erasure(elem.asType());
            method.nextControlFlow("else if (_object instanceof $T _o)", elemErasure)
                .addStatement("$L.write(_gen, _o)", writerName);
        }

        method.nextControlFlow("else")
            .addStatement("throw new $T($S)", IllegalStateException.class, "Unsupported class")
            .endControlFlow();

        typeBuilder.addMethod(method.build());
    }
}
