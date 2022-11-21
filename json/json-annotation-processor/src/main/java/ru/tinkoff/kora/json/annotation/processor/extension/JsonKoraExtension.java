package ru.tinkoff.kora.json.annotation.processor.extension;

import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.json.annotation.processor.JsonProcessor;
import ru.tinkoff.kora.json.annotation.processor.JsonUtils;
import ru.tinkoff.kora.json.annotation.processor.KnownType;
import ru.tinkoff.kora.json.annotation.processor.reader.JsonReaderGenerator;
import ru.tinkoff.kora.json.annotation.processor.reader.ReaderTypeMetaParser;
import ru.tinkoff.kora.json.annotation.processor.reader.SealedInterfaceReaderGenerator;
import ru.tinkoff.kora.json.annotation.processor.writer.JsonWriterGenerator;
import ru.tinkoff.kora.json.annotation.processor.writer.SealedInterfaceWriterGenerator;
import ru.tinkoff.kora.json.annotation.processor.writer.WriterTypeMetaParser;
import ru.tinkoff.kora.json.common.JsonReader;
import ru.tinkoff.kora.json.common.JsonWriter;
import ru.tinkoff.kora.json.common.annotation.Json;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionResult;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public class JsonKoraExtension implements KoraExtension {

    private final Types types;
    private final Elements elements;
    private final JsonProcessor processor;
    private final TypeMirror jsonWriterErasure;
    private final TypeMirror jsonReaderErasure;
    private final ReaderTypeMetaParser readerTypeMetaParser;
    private final WriterTypeMetaParser writerTypeMetaParser;

    public JsonKoraExtension(ProcessingEnvironment processingEnv) {
        this.types = processingEnv.getTypeUtils();
        this.elements = processingEnv.getElementUtils();
        var knownTypes = new KnownType(this.elements, this.types);
        this.readerTypeMetaParser = new ReaderTypeMetaParser(processingEnv, knownTypes);
        this.writerTypeMetaParser = new WriterTypeMetaParser(processingEnv, knownTypes);

        var writerGenerator = new JsonWriterGenerator(processingEnv);
        var readerGenerator = new JsonReaderGenerator(processingEnv);
        var sealedReaderGenerator = new SealedInterfaceReaderGenerator(processingEnv);
        var sealedWriterGenerator = new SealedInterfaceWriterGenerator(processingEnv);
        this.processor = new JsonProcessor(
            processingEnv,
            this.readerTypeMetaParser,
            this.writerTypeMetaParser,
            writerGenerator,
            readerGenerator,
            sealedReaderGenerator,
            sealedWriterGenerator);
        this.jsonWriterErasure = this.types.erasure(this.elements.getTypeElement(JsonWriter.class.getCanonicalName()).asType());
        this.jsonReaderErasure = this.types.erasure(this.elements.getTypeElement(JsonReader.class.getCanonicalName()).asType());
    }

    @Override
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror) {
        var erasure = this.types.erasure(typeMirror);
        if (this.types.isSameType(erasure, this.jsonWriterErasure)) {
            var writerType = (DeclaredType) typeMirror;
            var possibleJsonClass = writerType.getTypeArguments().get(0);

            if (possibleJsonClass.getKind() != TypeKind.DECLARED) {
                return null;
            }

            var writerMeta = this.writerTypeMetaParser.parse(possibleJsonClass);
            if (writerMeta != null && (writerMeta.isSealedStructure() || this.isProcessableType(writerMeta.typeElement()))) {
                return () -> this.generateWriter(possibleJsonClass);
            }
        }
        if (this.types.isSameType(erasure, this.jsonReaderErasure)) {
            var readerType = (DeclaredType) typeMirror;
            var possibleJsonClass = readerType.getTypeArguments().get(0);

            if (possibleJsonClass.getKind() != TypeKind.DECLARED) {
                return null;
            }

            var readerMeta = this.readerTypeMetaParser.parse(possibleJsonClass);
            if (readerMeta == null) {
                return null;
            } else if (readerMeta.isSealedStructure() && readerMeta.discriminatorField() != null) {
                return () -> this.generateReader(possibleJsonClass);
            } else if (this.isProcessableType(readerMeta.typeElement())) {
                return () -> this.generateReader(possibleJsonClass);
            }
        }
        return null;
    }


    @Nullable
    private ExtensionResult generateReader(TypeMirror jsonClass) {
        var jsonTypeElement = (TypeElement) this.types.asElement(jsonClass);
        var packageElement = this.elements.getPackageOf(jsonTypeElement).getQualifiedName().toString();
        var resultClassName = JsonUtils.jsonReaderName(this.types, jsonClass);
        var resultElement = this.elements.getTypeElement(packageElement + "." + resultClassName);
        if (resultElement != null) {
            return buildExtensionResult((DeclaredType) jsonClass, resultElement);
        }

        var hasJsonConstructor = CommonUtils.findConstructors(jsonTypeElement, s -> !s.contains(Modifier.PRIVATE))
            .stream()
            .anyMatch(e -> e.getAnnotation(ru.tinkoff.kora.json.common.annotation.JsonReader.class) != null);
        if (hasJsonConstructor || jsonTypeElement.getAnnotation(ru.tinkoff.kora.json.common.annotation.JsonReader.class) != null) {
            // annotation processor will handle that
            return ExtensionResult.nextRound();
        }

        this.processor.generateReader(jsonTypeElement);
        return ExtensionResult.nextRound();
    }

    @Nullable
    private ExtensionResult generateWriter(TypeMirror jsonClass) {
        var jsonTypeElement = (TypeElement) this.types.asElement(jsonClass);
        var packageElement = this.elements.getPackageOf(jsonTypeElement).getQualifiedName().toString();
        var resultClassName = JsonUtils.jsonWriterName(this.types, jsonClass);
        var resultElement = this.elements.getTypeElement(packageElement + "." + resultClassName);
        if (resultElement != null) {
            return buildExtensionResult((DeclaredType) jsonClass, resultElement);
        }

        if (jsonTypeElement.getAnnotation(Json.class) != null || jsonTypeElement.getAnnotation(ru.tinkoff.kora.json.common.annotation.JsonWriter.class) != null) {
            // annotation processor will handle that
            return ExtensionResult.nextRound();
        }

        this.processor.generateWriter(jsonTypeElement);
        return ExtensionResult.nextRound();
    }

    private ExtensionResult buildExtensionResult(DeclaredType jsonClass, TypeElement resultElement) {
        var constructor = findDefaultConstructor(resultElement);

        if (resultElement.getTypeParameters().isEmpty()) {
            return ExtensionResult.fromExecutable(constructor);
        }

        var typeTypeParameters = jsonClass.getTypeArguments();
        var declaredType = this.types.getDeclaredType(resultElement, typeTypeParameters.toArray(new TypeMirror[0]));
        var constructorType = (ExecutableType) this.types.asMemberOf(declaredType, constructor);
        return ExtensionResult.fromExecutable(constructor, constructorType);
    }


    private ExecutableElement findDefaultConstructor(TypeElement resultElement) {
        return resultElement.getEnclosedElements()
            .stream()
            .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR)
            .map(ExecutableElement.class::cast)
            .findFirst()
            .orElseThrow();
    }

    private boolean isProcessableType(Element element) {
        var elementKind = element.getKind();
        if (elementKind == ElementKind.INTERFACE) {
            return false;
        }
        if (elementKind == ElementKind.RECORD || elementKind == ElementKind.ENUM) {
            return true;
        }
        return elementKind == ElementKind.CLASS && element.getModifiers().contains(Modifier.FINAL);
    }
}
