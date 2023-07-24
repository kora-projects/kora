package ru.tinkoff.kora.json.annotation.processor.extension;

import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.json.annotation.processor.JsonProcessor;
import ru.tinkoff.kora.json.annotation.processor.JsonTypes;
import ru.tinkoff.kora.json.annotation.processor.JsonUtils;
import ru.tinkoff.kora.json.annotation.processor.KnownType;
import ru.tinkoff.kora.json.annotation.processor.reader.ReaderTypeMetaParser;
import ru.tinkoff.kora.json.annotation.processor.writer.WriterTypeMetaParser;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionResult;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.Objects;
import java.util.Set;

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
        var knownTypes = new KnownType();
        this.readerTypeMetaParser = new ReaderTypeMetaParser(processingEnv, knownTypes);
        this.writerTypeMetaParser = new WriterTypeMetaParser(processingEnv, knownTypes);

        this.processor = new JsonProcessor(processingEnv);
        this.jsonWriterErasure = this.types.erasure(this.elements.getTypeElement(JsonTypes.jsonWriter.canonicalName()).asType());
        this.jsonReaderErasure = this.types.erasure(this.elements.getTypeElement(JsonTypes.jsonReader.canonicalName()).asType());
    }

    @Override
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror, Set<String> tags) {
        if (!tags.isEmpty()) return null;
        var erasure = this.types.erasure(typeMirror);
        if (this.types.isSameType(erasure, this.jsonWriterErasure)) {
            var writerType = (DeclaredType) typeMirror;
            var possibleJsonClass = writerType.getTypeArguments().get(0);
            if (possibleJsonClass.getKind() != TypeKind.DECLARED) {
                return null;
            }
            var jsonElement = (TypeElement) this.types.asElement(possibleJsonClass);
            if (AnnotationUtils.findAnnotation(jsonElement, JsonTypes.json) != null || AnnotationUtils.findAnnotation(jsonElement, JsonTypes.jsonWriterAnnotation) != null) {
                return KoraExtensionDependencyGenerator.generatedFrom(elements, jsonElement, "JsonWriter");
            }
            if (jsonElement.getModifiers().contains(Modifier.SEALED) || jsonElement.getKind() == ElementKind.ENUM) {
                return () -> this.generateWriter(possibleJsonClass);
            }
            try {
                Objects.requireNonNull(this.writerTypeMetaParser.parse(jsonElement, possibleJsonClass));
                return () -> this.generateWriter(possibleJsonClass);
            } catch (ProcessingErrorException e) {
                return null;
            }
        }
        if (this.types.isSameType(erasure, this.jsonReaderErasure)) {
            var readerType = (DeclaredType) typeMirror;
            var possibleJsonClass = readerType.getTypeArguments().get(0);
            if (possibleJsonClass.getKind() != TypeKind.DECLARED) {
                return null;
            }
            var typeElement = (TypeElement) types.asElement(possibleJsonClass);
            if (AnnotationUtils.findAnnotation(typeElement, JsonTypes.json) != null
                || AnnotationUtils.findAnnotation(typeElement, JsonTypes.jsonReaderAnnotation) != null
                || CommonUtils.findConstructors(typeElement, s -> s.contains(Modifier.PUBLIC))
                    .stream()
                    .anyMatch(e -> AnnotationUtils.findAnnotation(e, JsonTypes.jsonReaderAnnotation) != null)) {
                return KoraExtensionDependencyGenerator.generatedFrom(elements, typeElement, "JsonReader");
            }

            if (typeElement.getModifiers().contains(Modifier.SEALED) || typeElement.getKind() == ElementKind.ENUM) {
                return () -> this.generateReader(possibleJsonClass);
            }
            try {
                Objects.requireNonNull(this.readerTypeMetaParser.parse(typeElement, typeMirror));
                return () -> this.generateReader(possibleJsonClass);
            } catch (ProcessingErrorException e) {
                return null;
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
            return buildExtensionResult(resultElement);
        }

        var hasJsonConstructor = CommonUtils.findConstructors(jsonTypeElement, s -> !s.contains(Modifier.PRIVATE))
            .stream()
            .anyMatch(e -> AnnotationUtils.findAnnotation(e, JsonTypes.jsonReaderAnnotation) != null);
        if (hasJsonConstructor || AnnotationUtils.findAnnotation(jsonTypeElement, JsonTypes.jsonReaderAnnotation) != null) {
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
            return buildExtensionResult(resultElement);
        }

        if (AnnotationUtils.findAnnotation(jsonTypeElement, JsonTypes.json) != null || AnnotationUtils.findAnnotation(jsonTypeElement, JsonTypes.jsonWriterAnnotation) != null) {
            // annotation processor will handle that
            return ExtensionResult.nextRound();
        }

        this.processor.generateWriter(jsonTypeElement);
        return ExtensionResult.nextRound();
    }

    private ExtensionResult buildExtensionResult(TypeElement resultElement) {
        var constructor = findDefaultConstructor(resultElement);

        return ExtensionResult.fromExecutable(constructor);
    }


    private ExecutableElement findDefaultConstructor(TypeElement resultElement) {
        return resultElement.getEnclosedElements()
            .stream()
            .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR)
            .map(ExecutableElement.class::cast)
            .findFirst()
            .orElseThrow();
    }
}
