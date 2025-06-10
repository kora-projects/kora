package ru.tinkoff.kora.json.annotation.processor;

import com.squareup.javapoet.ClassName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.LogUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JsonAnnotationProcessor extends AbstractKoraProcessor {

    private static final Logger log = LoggerFactory.getLogger(JsonAnnotationProcessor.class);

    private JsonProcessor processor;

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(
            JsonTypes.json,
            JsonTypes.jsonReaderAnnotation,
            JsonTypes.jsonWriterAnnotation
        );
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.processor = new JsonProcessor(processingEnv);
    }

    @Override
    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        var jsonElements = annotatedElements.getOrDefault(JsonTypes.json, List.of());
        LogUtils.logAnnotatedElementsFull(log, Level.DEBUG, "Generating Json Readers & Writers for", jsonElements);
        for (var annotated : jsonElements) {
            if (annotated.element().getKind().isClass() || annotated.element().getKind().isInterface() && annotated.element().getModifiers().contains(Modifier.SEALED)) {
                var te = (TypeElement) annotated.element();
                try {
                    this.processor.generateReader(te);
                } catch (ProcessingErrorException ex) {
                    ex.printError(this.processingEnv);
                }
                try {
                    this.processor.generateWriter(te);
                } catch (ProcessingErrorException ex) {
                    ex.printError(this.processingEnv);
                }
            }
        }
        var jsonWriterElements = annotatedElements.getOrDefault(JsonTypes.jsonWriterAnnotation, List.of());
        LogUtils.logAnnotatedElementsFull(log, Level.DEBUG, "Generating JsonWriters for", jsonWriterElements);
        for (var annotated : jsonWriterElements) {
            var element = annotated.element();
            if (element.getKind().isClass() || element.getKind().isInterface() && element.getModifiers().contains(Modifier.SEALED)) {
                if (AnnotationUtils.findAnnotation(element, JsonTypes.json) == null) {
                    try {
                        this.processor.generateWriter((TypeElement) element);
                    } catch (ProcessingErrorException ex) {
                        ex.printError(this.processingEnv);
                    }
                }
            } else {
                messager.printMessage(Diagnostic.Kind.ERROR, "Only classes and interfaces can be annotated with @JsonWriter, got " + annotated.element().getKind(), annotated.element());
            }
        }
        var jsonReaderElements = annotatedElements.getOrDefault(JsonTypes.jsonReaderAnnotation, List.of());
        LogUtils.logAnnotatedElementsFull(log, Level.DEBUG, "Generating JsonReaders for", jsonReaderElements);
        for (var annotated : jsonReaderElements) {
            var element = annotated.element();
            if (element.getKind() == ElementKind.CONSTRUCTOR) {
                element = element.getEnclosingElement();
            }
            if (AnnotationUtils.isAnnotationPresent(element, JsonTypes.json)) {
                continue;
            }
            if (element.getKind().isClass() || element.getKind().isInterface() && element.getModifiers().contains(Modifier.SEALED)) {
                try {
                    this.processor.generateReader((TypeElement) element);
                } catch (ProcessingErrorException ex) {
                    ex.printError(this.processingEnv);
                }
            } else {
                messager.printMessage(Diagnostic.Kind.ERROR, "Only classes and sealed interfaces can be annotated with @JsonReader, got " + element.getKind(), annotated.element());
            }
        }
    }
}
