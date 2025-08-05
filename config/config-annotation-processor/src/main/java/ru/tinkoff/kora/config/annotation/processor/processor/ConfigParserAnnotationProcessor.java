package ru.tinkoff.kora.config.annotation.processor.processor;

import com.squareup.javapoet.ClassName;
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
import java.util.*;

public class ConfigParserAnnotationProcessor extends AbstractKoraProcessor {

    private static final Logger log = LoggerFactory.getLogger(ConfigParserAnnotationProcessor.class);

    private ConfigParserGenerator configParserGenerator;

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(ConfigClassNames.configValueExtractorAnnotation, ConfigClassNames.configSourceAnnotation);
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.configParserGenerator = new ConfigParserGenerator(processingEnv);
    }

    @Override
    protected void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        var elementsToProcess = new HashMap<ClassName, TypeElement>();
        for (var annotatedElementList : annotatedElements.values()) {
            for (var annotatedElement : annotatedElementList) {
                if (annotatedElement.element() instanceof TypeElement te) {
                    var className = ClassName.get(te);
                    elementsToProcess.put(className, te);
                } else {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "@" + ConfigClassNames.configValueExtractorAnnotation.simpleName() + " is applicable only to records, classes or interfaces");
                }
            }
        }
        var elements = new ArrayList<>(elementsToProcess.values());
        LogUtils.logElementsFull(log, Level.DEBUG, "Generating ConfigValueExtractor for", elements);
        for (var element : elements) {
            if (element.getKind() == ElementKind.INTERFACE) {
                var result = configParserGenerator.generateForInterface((DeclaredType) element.asType());
                if (result.isRight()) {
                    for (var processingError : Objects.requireNonNull(result.right())) {
                        processingError.print(this.processingEnv);
                    }
                }
            } else if (element.getKind() == ElementKind.RECORD) {
                configParserGenerator.generateForRecord((DeclaredType) element.asType());
            } else if (element.getKind() == ElementKind.CLASS) {
                var result = configParserGenerator.generateForPojo((DeclaredType) element.asType());
                if (result.isRight()) {
                    for (var processingError : Objects.requireNonNull(result.right())) {
                        processingError.print(this.processingEnv);
                    }
                }
            } else {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "@" + ConfigClassNames.configValueExtractorAnnotation.simpleName() + " is applicable only to records, classes or interfaces");
            }
        }
    }
}
