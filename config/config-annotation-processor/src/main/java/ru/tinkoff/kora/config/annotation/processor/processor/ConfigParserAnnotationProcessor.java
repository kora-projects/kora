package ru.tinkoff.kora.config.annotation.processor.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.LogUtils;
import ru.tinkoff.kora.config.annotation.processor.ConfigClassNames;
import ru.tinkoff.kora.config.annotation.processor.ConfigParserGenerator;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.tools.Diagnostic;
import java.util.Objects;
import java.util.Set;

public class ConfigParserAnnotationProcessor extends AbstractKoraProcessor {

    private static final Logger log = LoggerFactory.getLogger(ConfigParserAnnotationProcessor.class);

    private TypeElement configValueExtractorAnnotation;
    private TypeElement configSourceAnnotation;
    private ConfigParserGenerator configParserGenerator;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(ConfigClassNames.configValueExtractorAnnotation.canonicalName(), ConfigClassNames.configSourceAnnotation.canonicalName());
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.configValueExtractorAnnotation = processingEnv.getElementUtils().getTypeElement(ConfigClassNames.configValueExtractorAnnotation.canonicalName());
        this.configSourceAnnotation = processingEnv.getElementUtils().getTypeElement(ConfigClassNames.configSourceAnnotation.canonicalName());
        this.configParserGenerator = new ConfigParserGenerator(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (this.configValueExtractorAnnotation == null) {
            return false;
        }

        var elements = roundEnv.getElementsAnnotatedWithAny(this.configValueExtractorAnnotation, this.configSourceAnnotation);
        LogUtils.logElementsFull(log, Level.DEBUG, "Generating ConfigValueExtractor for", elements);
        for (var element : elements) {
            if (element.getKind() == ElementKind.INTERFACE) {
                var result = configParserGenerator.generateForInterface(roundEnv, (DeclaredType) element.asType());
                if (result.isRight()) {
                    for (var processingError : Objects.requireNonNull(result.right())) {
                        processingError.print(this.processingEnv);
                    }
                }
            } else if (element.getKind() == ElementKind.RECORD) {
                configParserGenerator.generateForRecord(roundEnv, (DeclaredType) element.asType());
            } else if (element.getKind() == ElementKind.CLASS) {
                var result = configParserGenerator.generateForPojo(roundEnv, (DeclaredType) element.asType());
                if (result.isRight()) {
                    for (var processingError : Objects.requireNonNull(result.right())) {
                        processingError.print(this.processingEnv);
                    }
                }
            } else {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "@" + ConfigClassNames.configValueExtractorAnnotation.simpleName() + " is applicable only to records, classes or interfaces");
            }
        }

        return false;
    }
}
