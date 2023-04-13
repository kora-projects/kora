package ru.tinkoff.kora.json.annotation.processor;

import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.util.Objects;
import java.util.Set;

public class JsonAnnotationProcessor extends AbstractKoraProcessor {
    private boolean initialized = false;
    private JsonProcessor processor;
    private TypeElement jsonAnnotation;
    private TypeElement jsonWriterAnnotation;
    private TypeElement jsonReaderAnnotation;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(
            JsonTypes.json.canonicalName(),
            JsonTypes.jsonReaderAnnotation.canonicalName(),
            JsonTypes.jsonWriterAnnotation.canonicalName()
        );
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.jsonAnnotation = processingEnv.getElementUtils().getTypeElement(JsonTypes.json.canonicalName());
        if (this.jsonAnnotation == null) {
            return;
        }
        this.jsonWriterAnnotation = Objects.requireNonNull(processingEnv.getElementUtils().getTypeElement(JsonTypes.jsonWriterAnnotation.canonicalName()));
        this.jsonReaderAnnotation = Objects.requireNonNull(processingEnv.getElementUtils().getTypeElement(JsonTypes.jsonReaderAnnotation.canonicalName()));
        this.initialized = true;
        this.processor = new JsonProcessor(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!this.initialized) {
            return false;
        }
        if (roundEnv.processingOver()) {
            return false;
        }
        for (var e : roundEnv.getElementsAnnotatedWith(this.jsonAnnotation)) {
            if (e.getKind().isClass() || e.getKind() == ElementKind.INTERFACE) {
                try {
                    this.processor.generateReader((TypeElement) e);
                } catch (ProcessingErrorException ex) {
                    ex.printError(this.processingEnv);
                }
                try {
                    this.processor.generateWriter((TypeElement) e);
                } catch (ProcessingErrorException ex) {
                    ex.printError(this.processingEnv);
                }
            }
        }
        for (var e : roundEnv.getElementsAnnotatedWith(this.jsonWriterAnnotation)) {
            if (AnnotationUtils.findAnnotation(e, JsonTypes.json) != null) continue;
            if (e.getKind().isClass() || e.getKind() == ElementKind.INTERFACE) {
                try {
                    this.processor.generateWriter((TypeElement) e);
                } catch (ProcessingErrorException ex) {
                    ex.printError(this.processingEnv);
                }
            }
        }
        for (var e : roundEnv.getElementsAnnotatedWith(this.jsonReaderAnnotation)) {
            if (AnnotationUtils.findAnnotation(e, JsonTypes.json) != null) continue;
            if (e.getKind() == ElementKind.CONSTRUCTOR) {
                var typeElement = (TypeElement) e.getEnclosingElement();
                if (AnnotationUtils.findAnnotation(typeElement, JsonTypes.json) != null) continue;
                try {
                    this.processor.generateReader(typeElement);
                } catch (ProcessingErrorException ex) {
                    ex.printError(this.processingEnv);
                }
            } else if (e.getKind().isClass()) {
                try {
                    this.processor.generateReader((TypeElement) e);
                } catch (ProcessingErrorException ex) {
                    ex.printError(this.processingEnv);
                }
            }
        }
        return false;
    }
}
