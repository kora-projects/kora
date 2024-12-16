package ru.tinkoff.kora.avro.annotation.processor.extension;

import com.squareup.javapoet.ClassName;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.avro.annotation.processor.AvroTypes;
import ru.tinkoff.kora.avro.annotation.processor.AvroUtils;
import ru.tinkoff.kora.avro.annotation.processor.reader.AvroReaderGenerator;
import ru.tinkoff.kora.avro.annotation.processor.writer.AvroWriterGenerator;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionResult;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

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
import java.util.Set;

public class AvroExtension implements KoraExtension {

    private final Types types;
    private final Elements elements;
    private final TypeMirror writerErasure;
    private final TypeMirror readerErasure;
    private final AvroReaderGenerator readerGenerator;
    private final AvroWriterGenerator writerGenerator;

    public AvroExtension(ProcessingEnvironment processingEnv) {
        this.types = processingEnv.getTypeUtils();
        this.elements = processingEnv.getElementUtils();

        this.writerErasure = this.types.erasure(this.elements.getTypeElement(AvroTypes.writer.canonicalName()).asType());
        this.readerErasure = this.types.erasure(this.elements.getTypeElement(AvroTypes.reader.canonicalName()).asType());
        this.readerGenerator = new AvroReaderGenerator(processingEnv);
        this.writerGenerator = new AvroWriterGenerator(processingEnv);
    }

    @Nullable
    @Override
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror, Set<String> tags) {
        boolean isBinary = tags.isEmpty() || isBinary(tags);
        boolean isJson = isJson(tags);
        if (!isBinary && !isJson) {
            return null;
        }

        var erasure = this.types.erasure(typeMirror);
        if (this.types.isSameType(erasure, this.writerErasure)) {
            var writerType = (DeclaredType) typeMirror;
            var targetType = writerType.getTypeArguments().get(0);
            if (targetType.getKind() != TypeKind.DECLARED) {
                return null;
            }

            var targetElement = (TypeElement) this.types.asElement(targetType);
            if (isBinary && AnnotationUtils.findAnnotation(targetElement, AvroTypes.avroBinary) != null) {
                return KoraExtensionDependencyGenerator.generatedFrom(elements, targetElement, AvroTypes.writer);
            }
            if (isJson && AnnotationUtils.findAnnotation(targetElement, AvroTypes.avroJson) != null) {
                return KoraExtensionDependencyGenerator.generatedFrom(elements, targetElement, AvroTypes.writer);
            }

            if (targetElement.getKind() == ElementKind.ENUM
                || targetElement.getKind().isInterface()
                || targetElement.getModifiers().contains(Modifier.ABSTRACT)) {
                return null;
            }

            try {
                return () -> this.generateWriter(targetType, tags, isBinary);
            } catch (ProcessingErrorException e) {
                return null;
            }
        }

        if (this.types.isSameType(erasure, this.readerErasure)) {
            var readerType = (DeclaredType) typeMirror;
            var targetType = readerType.getTypeArguments().get(0);
            if (targetType.getKind() != TypeKind.DECLARED) {
                return null;
            }

            var targetElement = (TypeElement) types.asElement(targetType);
            if (isBinary && AnnotationUtils.findAnnotation(targetElement, AvroTypes.avroBinary) != null) {
                return KoraExtensionDependencyGenerator.generatedFrom(elements, targetElement, AvroTypes.reader);
            }
            if (isJson && AnnotationUtils.findAnnotation(targetElement, AvroTypes.avroJson) != null) {
                return KoraExtensionDependencyGenerator.generatedFrom(elements, targetElement, AvroTypes.reader);
            }

            if (targetElement.getKind() == ElementKind.ENUM
                || targetElement.getKind().isInterface()
                || targetElement.getModifiers().contains(Modifier.ABSTRACT)) {
                return null;
            }

            try {
                return () -> this.generateReader(targetType, tags, isBinary);
            } catch (ProcessingErrorException e) {
                return null;
            }
        }
        return null;
    }

    private boolean isBinary(Set<String> tags) {
        return tags.equals(Set.of(AvroTypes.avroBinary.canonicalName()));
    }

    private boolean isJson(Set<String> tags) {
        return tags.equals(Set.of(AvroTypes.avroJson.canonicalName()));
    }

    @Nullable
    private ExtensionResult generateReader(TypeMirror typeMirror, Set<String> tags, boolean isBinary) {
        var element = (TypeElement) this.types.asElement(typeMirror);
        var packageElement = this.elements.getPackageOf(element).getQualifiedName().toString();
        var resultClassName = isBinary
            ? AvroUtils.readerBinaryName(this.types, typeMirror)
            : AvroUtils.readerJsonName(this.types, typeMirror);
        var resultElement = this.elements.getTypeElement(packageElement + "." + resultClassName);
        if (resultElement != null) {
            return buildExtensionResult(resultElement, tags);
        }

        ClassName annotation = isBinary ? AvroTypes.avroBinary : AvroTypes.avroJson;
        if (AnnotationUtils.findAnnotation(element, annotation) != null) {
            // annotation processor will handle that
            return ExtensionResult.nextRound();
        }

        if (isBinary) {
            this.readerGenerator.generateBinary(element);
        } else {
            this.readerGenerator.generateJson(element);
        }
        return ExtensionResult.nextRound();
    }

    @Nullable
    private ExtensionResult generateWriter(TypeMirror typeMirror, Set<String> tags, boolean isBinary) {
        var element = (TypeElement) this.types.asElement(typeMirror);
        var packageElement = this.elements.getPackageOf(element).getQualifiedName().toString();
        var resultClassName = isBinary
            ? AvroUtils.writerBinaryName(this.types, typeMirror)
            : AvroUtils.writerJsonName(this.types, typeMirror);
        var resultElement = this.elements.getTypeElement(packageElement + "." + resultClassName);
        if (resultElement != null) {
            return buildExtensionResult(resultElement, tags);
        }

        ClassName annotation = isBinary ? AvroTypes.avroBinary : AvroTypes.avroJson;
        if (AnnotationUtils.findAnnotation(element, annotation) != null) {
            // annotation processor will handle that
            return ExtensionResult.nextRound();
        }

        if (isBinary) {
            this.writerGenerator.generateBinary(element);
        } else {
            this.writerGenerator.generateJson(element);
        }
        return ExtensionResult.nextRound();
    }

    private ExtensionResult buildExtensionResult(TypeElement resultElement, Set<String> tags) {
        var constructor = findDefaultConstructor(resultElement);
        return ExtensionResult.fromExecutable(constructor, tags);
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
