package ru.tinkoff.kora.avro.annotation.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.LogUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.avro.annotation.processor.reader.AvroReaderGenerator;
import ru.tinkoff.kora.avro.annotation.processor.writer.AvroWriterGenerator;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.util.Set;

public class AvroAnnotationProcessor extends AbstractKoraProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AvroAnnotationProcessor.class);

    private boolean initialized = false;
    private TypeElement avroBinaryAnnotation;
    private TypeElement avroJsonAnnotation;
    private AvroWriterGenerator writerGenerator;
    private AvroReaderGenerator readerGenerator;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(AvroTypes.avroBinary.canonicalName(), AvroTypes.avroJson.canonicalName());
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.avroBinaryAnnotation = processingEnv.getElementUtils().getTypeElement(AvroTypes.avroBinary.canonicalName());
        if (this.avroBinaryAnnotation == null) {
            return;
        }

        this.avroJsonAnnotation = processingEnv.getElementUtils().getTypeElement(AvroTypes.avroJson.canonicalName());
        this.initialized = true;
        this.writerGenerator = new AvroWriterGenerator(processingEnv);
        this.readerGenerator = new AvroReaderGenerator(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!this.initialized) {
            return false;
        }
        if (roundEnv.processingOver()) {
            return false;
        }

        var avroBinaryElements = roundEnv.getElementsAnnotatedWith(this.avroBinaryAnnotation).stream()
            .filter(e -> e.getKind().isClass() || e.getKind() == ElementKind.INTERFACE)
            .toList();
        if(!avroBinaryElements.isEmpty()) {
            LogUtils.logElementsFull(logger, Level.DEBUG, "Generating Avro Binary Readers & Writers for", avroBinaryElements);
            for (var e : avroBinaryElements) {
                try {
                    this.readerGenerator.generateBinary((TypeElement) e);
                } catch (ProcessingErrorException ex) {
                    ex.printError(this.processingEnv);
                }
                try {
                    this.writerGenerator.generateBinary((TypeElement) e);
                } catch (ProcessingErrorException ex) {
                    ex.printError(this.processingEnv);
                }
            }
        }

        var avroJsonElements = roundEnv.getElementsAnnotatedWith(this.avroJsonAnnotation).stream()
            .filter(e -> e.getKind().isClass() || e.getKind() == ElementKind.INTERFACE)
            .toList();
        if(!avroJsonElements.isEmpty()) {
            LogUtils.logElementsFull(logger, Level.DEBUG, "Generating Avro Json Readers & Writers for", avroBinaryElements);
            for (var e : avroJsonElements) {
                try {
                    this.readerGenerator.generateJson((TypeElement) e);
                } catch (ProcessingErrorException ex) {
                    ex.printError(this.processingEnv);
                }
                try {
                    this.writerGenerator.generateJson((TypeElement) e);
                } catch (ProcessingErrorException ex) {
                    ex.printError(this.processingEnv);
                }
            }
        }

        return false;
    }
}
