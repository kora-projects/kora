package io.koraframework.json.annotation.processor;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.*;
import org.slf4j.*;
import org.slf4j.event.*;

import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.tools.*;
import java.util.*;

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
        var requests = new java.util.LinkedHashMap<String, CodecRequest>();

        var jsonElements = annotatedElements.getOrDefault(JsonTypes.json, List.of());
        LogUtils.logAnnotatedElementsFull(log, Level.DEBUG, "Generating Json Readers & Writers for", jsonElements);
        for (var annotated : jsonElements) {
            if (annotated.element().getKind().isClass() || annotated.element().getKind().isInterface() && annotated.element().getModifiers().contains(Modifier.SEALED)) {
                var te = (TypeElement) annotated.element();
                var fqn = te.getQualifiedName().toString();
                requests.merge(fqn, new CodecRequest(te, true, true), (old, cur) -> old.merge(true, true));
            }
        }

        var jsonWriterElements = annotatedElements.getOrDefault(JsonTypes.jsonWriterAnnotation, List.of());
        LogUtils.logAnnotatedElementsFull(log, Level.DEBUG, "Generating JsonWriters for", jsonWriterElements);
        for (var annotated : jsonWriterElements) {
            var element = annotated.element();
            if (element.getKind() == ElementKind.METHOD) {
                var enclosing = element.getEnclosingElement();
                if (!enclosing.getKind().isClass()) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                        "@JsonWriter on a method is supported only for a method of a class or enum, got enclosing " + enclosing.getKind(),
                        annotated.element());
                    continue;
                }
                element = enclosing;
            }
            if (element.getKind().isClass() || element.getKind().isInterface() && element.getModifiers().contains(Modifier.SEALED)) {
                var te = (TypeElement) element;
                var fqn = te.getQualifiedName().toString();
                requests.merge(fqn, new CodecRequest(te, false, true), (old, cur) -> old.merge(false, true));
            } else {
                messager.printMessage(Diagnostic.Kind.ERROR, "Only classes, interfaces and enum methods can be annotated with @JsonWriter, got " + element.getKind(), annotated.element());
            }
        }

        var jsonReaderElements = annotatedElements.getOrDefault(JsonTypes.jsonReaderAnnotation, List.of());
        LogUtils.logAnnotatedElementsFull(log, Level.DEBUG, "Generating JsonReaders for", jsonReaderElements);
        for (var annotated : jsonReaderElements) {
            var element = annotated.element();
            if (element.getKind() == ElementKind.CONSTRUCTOR) {
                element = element.getEnclosingElement();
            } else if (element.getKind() == ElementKind.METHOD) {
                var enclosing = element.getEnclosingElement();
                if (!enclosing.getKind().isClass()) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                        "@JsonReader on a method is supported only for a static factory method of a class or enum, got enclosing " + enclosing.getKind(),
                        annotated.element());
                    continue;
                }
                element = enclosing;
            }
            if (element.getKind().isClass() || element.getKind().isInterface() && element.getModifiers().contains(Modifier.SEALED)) {
                var te = (TypeElement) element;
                var fqn = te.getQualifiedName().toString();
                requests.merge(fqn, new CodecRequest(te, true, false), (old, cur) -> old.merge(true, false));
            } else {
                messager.printMessage(Diagnostic.Kind.ERROR, "Only classes and sealed interfaces can be annotated with @JsonReader, got " + element.getKind(), annotated.element());
            }
        }

        for (var req : requests.values()) {
            try {
                this.processor.generateCodec(req.element(), req.needReader(), req.needWriter());
            } catch (ProcessingErrorException ex) {
                ex.printError(this.processingEnv);
            }
        }
    }

    private record CodecRequest(TypeElement element, boolean needReader, boolean needWriter) {
        CodecRequest merge(boolean r, boolean w) {
            return new CodecRequest(element, needReader || r, needWriter || w);
        }
    }
}
