package ru.tinkoff.kora.json.annotation.processor;

import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.json.annotation.processor.reader.JsonReaderGenerator;
import ru.tinkoff.kora.json.annotation.processor.reader.ReaderTypeMetaParser;
import ru.tinkoff.kora.json.annotation.processor.reader.SealedInterfaceReaderGenerator;
import ru.tinkoff.kora.json.annotation.processor.writer.JsonWriterGenerator;
import ru.tinkoff.kora.json.annotation.processor.writer.SealedInterfaceWriterGenerator;
import ru.tinkoff.kora.json.annotation.processor.writer.WriterTypeMetaParser;
import ru.tinkoff.kora.json.common.annotation.Json;
import ru.tinkoff.kora.json.common.annotation.JsonReader;
import ru.tinkoff.kora.json.common.annotation.JsonWriter;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class JsonAnnotationProcessor extends AbstractKoraProcessor {
    private boolean initialized = false;
    private JsonProcessor processor;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(
            Json.class.getCanonicalName(),
            JsonReader.class.getCanonicalName(),
            JsonWriter.class.getCanonicalName()
        );
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        var json = processingEnv.getElementUtils().getTypeElement(Json.class.getCanonicalName());
        if (json == null) {
            return;
        }
        this.initialized = true;

        var knownTypes = new KnownType(processingEnv.getElementUtils(), processingEnv.getTypeUtils());
        var readerTypeMetaParser = new ReaderTypeMetaParser(this.processingEnv, knownTypes);
        var writerTypeMetaParser = new WriterTypeMetaParser(processingEnv, knownTypes);
        var writerGenerator = new JsonWriterGenerator(this.processingEnv);
        var readerGenerator = new JsonReaderGenerator(this.processingEnv);
        var sealedReaderGenerator = new SealedInterfaceReaderGenerator(this.processingEnv);
        var sealedWriterGenerator = new SealedInterfaceWriterGenerator(this.processingEnv);
        this.processor = new JsonProcessor(
            processingEnv,
            readerTypeMetaParser,
            writerTypeMetaParser,
            writerGenerator,
            readerGenerator,
            sealedReaderGenerator,
            sealedWriterGenerator);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!this.initialized) {
            return false;
        }
        if (roundEnv.processingOver()) {
            this.processor.printError();
            return false;
        }
        var parsedAnnotations = annotations.stream()
            .map(a -> {
                if (a.getQualifiedName().contentEquals(Json.class.getCanonicalName())) {
                    return Json.class;
                }
                if (a.getQualifiedName().contentEquals(JsonWriter.class.getCanonicalName())) {
                    return JsonWriter.class;
                }
                if (a.getQualifiedName().contentEquals(JsonReader.class.getCanonicalName())) {
                    return JsonReader.class;
                }
                return null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (parsedAnnotations.contains(Json.class)) for (var e : roundEnv.getElementsAnnotatedWith(Json.class)) {
            this.processor.generateReader((TypeElement) e);
            this.processor.generateWriter((TypeElement) e);
        }
        if (parsedAnnotations.contains(JsonWriter.class)) for (var e : roundEnv.getElementsAnnotatedWith(JsonWriter.class)) {
            if (e.getKind() == ElementKind.CLASS || e.getKind() == ElementKind.RECORD) {
                this.processor.generateWriter((TypeElement) e);
            }
        }
        if (parsedAnnotations.contains(JsonReader.class)) for (var e : roundEnv.getElementsAnnotatedWith(JsonReader.class)) {
            if (e.getKind() == ElementKind.CONSTRUCTOR) {
                this.processor.generateReader((TypeElement) e.getEnclosingElement());
            } else if (e.getKind() == ElementKind.CLASS || e.getKind() == ElementKind.RECORD) {
                this.processor.generateReader((TypeElement) e);
            }
        }
        return false;
    }
}
