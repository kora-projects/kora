package io.koraframework.json.annotation.processor;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import io.koraframework.annotation.processor.common.AbstractKoraProcessor;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.LogUtils;
import io.koraframework.annotation.processor.common.ProcessingErrorException;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.LinkedHashMap;
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

    private static final class JsonElement {
        private final TypeElement element;
        private boolean reader;
        private boolean writer;

        private JsonElement(TypeElement element) {
            this.element = element;
        }
    }

    @Override
    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        // reader and writer of a type are written into one file, so we have to know everything requested for the type before generation
        var jsonTypes = new LinkedHashMap<String, JsonElement>();
        var jsonElements = annotatedElements.getOrDefault(JsonTypes.json, List.of());
        LogUtils.logAnnotatedElementsFull(log, Level.DEBUG, "Generating Json Readers & Writers for", jsonElements);
        for (var annotated : jsonElements) {
            if (annotated.element().getKind().isClass() || annotated.element().getKind().isInterface() && annotated.element().getModifiers().contains(Modifier.SEALED)) {
                var jsonType = jsonType(jsonTypes, (TypeElement) annotated.element());
                jsonType.reader = true;
                jsonType.writer = true;
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
            if (AnnotationUtils.isAnnotationPresent(element, JsonTypes.json)) {
                continue;
            }
            if (element.getKind().isClass() || element.getKind().isInterface() && element.getModifiers().contains(Modifier.SEALED)) {
                jsonType(jsonTypes, (TypeElement) element).writer = true;
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
            if (AnnotationUtils.isAnnotationPresent(element, JsonTypes.json)) {
                continue;
            }
            if (element.getKind().isClass() || element.getKind().isInterface() && element.getModifiers().contains(Modifier.SEALED)) {
                jsonType(jsonTypes, (TypeElement) element).reader = true;
            } else {
                messager.printMessage(Diagnostic.Kind.ERROR, "Only classes and sealed interfaces can be annotated with @JsonReader, got " + element.getKind(), annotated.element());
            }
        }

        for (var jsonType : jsonTypes.values()) {
            if (this.processor.isGenerated(jsonType.element)) {
                continue;
            }
            TypeSpec reader = null;
            TypeSpec writer = null;
            if (jsonType.reader) {
                try {
                    reader = this.processor.generateReader(jsonType.element);
                } catch (ProcessingErrorException ex) {
                    ex.printError(this.processingEnv);
                }
            }
            if (jsonType.writer) {
                try {
                    writer = this.processor.generateWriter(jsonType.element);
                } catch (ProcessingErrorException ex) {
                    ex.printError(this.processingEnv);
                }
            }
            this.processor.write(jsonType.element, reader, writer);
        }
    }

    private static JsonElement jsonType(Map<String, JsonElement> jsonTypes, TypeElement element) {
        return jsonTypes.computeIfAbsent(element.getQualifiedName().toString(), _ -> new JsonElement(element));
    }
}
