package ru.tinkoff.kora.json.annotation.processor.extension;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import javax.annotation.processing.Messager;
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
import javax.tools.Diagnostic;
import java.util.Objects;
import java.util.Set;

public class JsonKoraExtension implements KoraExtension {

    private static final Logger logger = LoggerFactory.getLogger(JsonKoraExtension.class);

    private final Types types;
    private final Elements elements;
    private final JsonProcessor processor;
    private final TypeMirror jsonWriterErasure;
    private final TypeMirror jsonReaderErasure;
    private final ReaderTypeMetaParser readerTypeMetaParser;
    private final WriterTypeMetaParser writerTypeMetaParser;
    private final Messager messager;

    public JsonKoraExtension(ProcessingEnvironment processingEnv) {
        this.types = processingEnv.getTypeUtils();
        this.elements = processingEnv.getElementUtils();
        var knownTypes = new KnownType();
        this.readerTypeMetaParser = new ReaderTypeMetaParser(processingEnv, knownTypes);
        this.writerTypeMetaParser = new WriterTypeMetaParser(processingEnv, knownTypes);

        this.processor = new JsonProcessor(processingEnv);
        this.jsonWriterErasure = this.types.erasure(this.elements.getTypeElement(JsonTypes.jsonWriter.canonicalName()).asType());
        this.jsonReaderErasure = this.types.erasure(this.elements.getTypeElement(JsonTypes.jsonReader.canonicalName()).asType());
        this.messager = processingEnv.getMessager();
    }

    @Override
    @Nullable
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
            if (JsonUtils.isNativePackage(elements, jsonElement)) {
                return null;
            }
            if (AnnotationUtils.findAnnotation(jsonElement, JsonTypes.json) != null || AnnotationUtils.findAnnotation(jsonElement, JsonTypes.jsonWriterAnnotation) != null) {
                return KoraExtensionDependencyGenerator.generatedFrom(elements, jsonElement, JsonTypes.jsonWriter);
            }

            if (jsonElement.getKind().isInterface() || jsonElement.getModifiers().contains(Modifier.ABSTRACT)) {
                if (jsonElement.getModifiers().contains(Modifier.SEALED)) {
                    return () -> this.generateWriter(jsonElement, possibleJsonClass);
                } else {
                    return null;
                }
            }

            if (jsonElement.getKind() == ElementKind.ENUM) {
                return () -> this.generateWriter(jsonElement, possibleJsonClass);
            }

            try {
                Objects.requireNonNull(this.writerTypeMetaParser.parse(jsonElement, possibleJsonClass));
                return () -> this.generateWriter(jsonElement, possibleJsonClass);
            } catch (ProcessingErrorException e) {
                logger.warn(e.getMessage(), e);
                return null;
            }
        }

        if (this.types.isSameType(erasure, this.jsonReaderErasure)) {
            var readerType = (DeclaredType) typeMirror;
            var possibleJsonClass = readerType.getTypeArguments().get(0);
            if (possibleJsonClass.getKind() != TypeKind.DECLARED) {
                return null;
            }
            var jsonElement = (TypeElement) types.asElement(possibleJsonClass);
            if (JsonUtils.isNativePackage(elements, jsonElement)) {
                return null;
            }
            if (AnnotationUtils.findAnnotation(jsonElement, JsonTypes.json) != null
                || AnnotationUtils.findAnnotation(jsonElement, JsonTypes.jsonReaderAnnotation) != null
                || CommonUtils.findConstructors(jsonElement, s -> s.contains(Modifier.PUBLIC))
                    .stream()
                    .anyMatch(e -> AnnotationUtils.findAnnotation(e, JsonTypes.jsonReaderAnnotation) != null)) {
                return KoraExtensionDependencyGenerator.generatedFrom(elements, jsonElement, JsonTypes.jsonReader);
            }

            if (jsonElement.getKind().isInterface() || jsonElement.getModifiers().contains(Modifier.ABSTRACT)) {
                if (jsonElement.getModifiers().contains(Modifier.SEALED)) {
                    return () -> this.generateReader(jsonElement, possibleJsonClass);
                } else {
                    return null;
                }
            }

            if (jsonElement.getKind() == ElementKind.ENUM) {
                return () -> this.generateReader(jsonElement, possibleJsonClass);
            }

            try {
                Objects.requireNonNull(this.readerTypeMetaParser.parse(jsonElement, typeMirror));
                return () -> this.generateReader(jsonElement, possibleJsonClass);
            } catch (ProcessingErrorException e) {
                logger.warn(e.getMessage());
                return null;
            }
        }
        return null;
    }

    @Nullable
    private ExtensionResult generateReader(TypeElement jsonElement, TypeMirror jsonType) {
        var packageElement = this.elements.getPackageOf(jsonElement).getQualifiedName().toString();
        var resultClassName = JsonUtils.jsonReaderName(this.types, jsonType);
        var resultElement = this.elements.getTypeElement(packageElement + "." + resultClassName);
        this.messager.printMessage(
            Diagnostic.Kind.WARNING,
            "Type is not annotated with @Json or @JsonWriter, but %s is requested by graph. Generating one in graph building process will lead to another round of compiling which will slow down you build".formatted(jsonType),
            jsonElement
        );
        if (resultElement != null) {
            return buildExtensionResult(resultElement);
        }

        var hasJsonConstructor = CommonUtils.findConstructors(jsonElement, s -> !s.contains(Modifier.PRIVATE))
            .stream()
            .anyMatch(e -> AnnotationUtils.findAnnotation(e, JsonTypes.jsonReaderAnnotation) != null);
        if (hasJsonConstructor || AnnotationUtils.findAnnotation(jsonElement, JsonTypes.jsonReaderAnnotation) != null) {
            // annotation processor will handle that
            return ExtensionResult.nextRound();
        }

        this.processor.generateReader(jsonElement);
        return ExtensionResult.nextRound();
    }

    @Nullable
    private ExtensionResult generateWriter(TypeElement jsonElement, TypeMirror jsonType) {
        this.messager.printMessage(
            Diagnostic.Kind.WARNING,
            "Type is not annotated with @Json or @JsonWriter, but %s is requested by graph. Generating one in graph building process will lead to another round of compiling which will slow down you build".formatted(jsonType),
            jsonElement
        );
        var packageElement = this.elements.getPackageOf(jsonElement).getQualifiedName().toString();
        var resultClassName = JsonUtils.jsonWriterName(this.types, jsonType);
        var resultElement = this.elements.getTypeElement(packageElement + "." + resultClassName);
        if (resultElement != null) {
            return buildExtensionResult(resultElement);
        }

        if (AnnotationUtils.findAnnotation(jsonElement, JsonTypes.json) != null || AnnotationUtils.findAnnotation(jsonElement, JsonTypes.jsonWriterAnnotation) != null) {
            // annotation processor will handle that
            return ExtensionResult.nextRound();
        }

        this.processor.generateWriter(jsonElement);
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
            .orElseThrow(() -> new ProcessingErrorException("No primary constructor found for " + resultElement, resultElement));
    }
}
