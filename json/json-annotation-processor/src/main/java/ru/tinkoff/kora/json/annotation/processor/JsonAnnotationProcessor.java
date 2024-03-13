package ru.tinkoff.kora.json.annotation.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.LogUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.common.annotation.Generated;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class JsonAnnotationProcessor extends AbstractKoraProcessor {

    private static final Logger log = LoggerFactory.getLogger(JsonAnnotationProcessor.class);

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

        var jsonElements = roundEnv.getElementsAnnotatedWith(this.jsonAnnotation).stream()
            .filter(e -> e.getKind().isClass() || e.getKind() == ElementKind.INTERFACE)
            .toList();
        LogUtils.logElementsFull(log, Level.DEBUG, "Generating Json Readers & Writers for", jsonElements);
        for (var e : jsonElements) {
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

        var jsonWriterElements = roundEnv.getElementsAnnotatedWith(this.jsonWriterAnnotation).stream()
            .filter(e -> e.getKind().isClass() || e.getKind() == ElementKind.INTERFACE)
            .filter(e -> AnnotationUtils.findAnnotation(e, JsonTypes.json) == null)
            .toList();
        LogUtils.logElementsFull(log, Level.DEBUG, "Generating JsonWriters for", jsonWriterElements);
        for (var e : jsonWriterElements) {
            try {
                this.processor.generateWriter((TypeElement) e);
            } catch (ProcessingErrorException ex) {
                ex.printError(this.processingEnv);
            }
        }

        var jsonReaderElements = roundEnv.getElementsAnnotatedWith(this.jsonReaderAnnotation).stream()
            .filter(e -> e.getKind().isClass() || e.getKind() == ElementKind.CONSTRUCTOR)
            .map(e -> (e.getKind() == ElementKind.CONSTRUCTOR)
                ? e.getEnclosingElement()
                : e)
            .filter(e -> AnnotationUtils.findAnnotation(e, JsonTypes.json) == null)
            .toList();
        LogUtils.logElementsFull(log, Level.DEBUG, "Generating JsonReaders for", jsonReaderElements);
        for (var e : jsonReaderElements) {
            try {
                this.processor.generateReader((TypeElement) e);
            } catch (ProcessingErrorException ex) {
                ex.printError(this.processingEnv);
            }
        }
        return false;
    }
}
