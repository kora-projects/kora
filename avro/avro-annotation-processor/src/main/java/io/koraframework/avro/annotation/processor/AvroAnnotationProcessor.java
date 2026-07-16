package io.koraframework.avro.annotation.processor;

import com.palantir.javapoet.ClassName;
import io.koraframework.annotation.processor.common.AbstractKoraProcessor;
import io.koraframework.annotation.processor.common.LogUtils;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.avro.annotation.processor.reader.AvroReaderGenerator;
import io.koraframework.avro.annotation.processor.writer.AvroWriterGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AvroAnnotationProcessor extends AbstractKoraProcessor {
    private static final Logger logger = LoggerFactory.getLogger(AvroAnnotationProcessor.class);

    private boolean initialized = false;
    private TypeElement avroAnnotation;
    private TypeElement specificRecordType;
    private AvroWriterGenerator writerGenerator;
    private AvroReaderGenerator readerGenerator;

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(AvroTypes.avro);
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.avroAnnotation = processingEnv.getElementUtils().getTypeElement(AvroTypes.avro.canonicalName());
        this.specificRecordType = processingEnv.getElementUtils().getTypeElement(AvroTypes.specificRecord.canonicalName());
        if (this.avroAnnotation == null || this.specificRecordType == null) {
            return;
        }

        this.initialized = true;
        this.writerGenerator = new AvroWriterGenerator(processingEnv);
        this.readerGenerator = new AvroReaderGenerator(processingEnv);
    }

    @Override
    protected void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        if (!this.initialized || roundEnv.processingOver()) {
            return;
        }

        var avroElements = roundEnv.getElementsAnnotatedWith(this.avroAnnotation).stream()
            .filter(e -> e.getKind().isClass() || e.getKind() == ElementKind.INTERFACE)
            .toList();
        if (avroElements.isEmpty()) {
            return;
        }

        LogUtils.logElementsFull(logger, Level.DEBUG, "Generating Avro Readers & Writers for", avroElements);
        for (var e : avroElements) {
            if (!this.types.isAssignable(e.asType(), this.specificRecordType.asType())) {
                new ProcessingErrorException("@Avro can only be used on org.apache.avro.specific.SpecificRecord types", e).printError(this.processingEnv);
                continue;
            }
            try {
                this.readerGenerator.generate((TypeElement) e);
            } catch (ProcessingErrorException ex) {
                ex.printError(this.processingEnv);
            }
            try {
                this.writerGenerator.generate((TypeElement) e);
            } catch (ProcessingErrorException ex) {
                ex.printError(this.processingEnv);
            }
        }
    }
}
