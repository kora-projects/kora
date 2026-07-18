package io.koraframework.logging.annotation.processor;

import com.palantir.javapoet.ClassName;
import io.koraframework.annotation.processor.common.AbstractKoraProcessor;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.logging.annotation.processor.aop.LogAspectClassNames;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LoggingAnnotationProcessor extends AbstractKoraProcessor {

    private MaskingMetadataProcessor maskingMetadataProcessor;

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(LogAspectClassNames.mask);
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.maskingMetadataProcessor = new MaskingMetadataProcessor(processingEnv);
    }

    @Override
    protected void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        var maskedElements = annotatedElements.getOrDefault(LogAspectClassNames.mask, List.of());
        for (var annotated : maskedElements) {
            var element = annotated.element();
            if (!(element instanceof TypeElement typeElement)) {
                continue;
            }
            if (!typeElement.getKind().isClass()) {
                this.messager.printMessage(Diagnostic.Kind.ERROR, "Only classes and records can be annotated with @Mask", element);
                continue;
            }
            if (typeElement.getModifiers().contains(Modifier.ABSTRACT)) {
                this.messager.printMessage(Diagnostic.Kind.ERROR, "Abstract classes can't be annotated with @Mask", element);
                continue;
            }
            if (AnnotationUtils.findAnnotation(typeElement, LogAspectClassNames.mask) == null) {
                continue;
            }
            try {
                this.maskingMetadataProcessor.generate(typeElement);
            } catch (ProcessingErrorException e) {
                e.printError(this.processingEnv);
            }
        }
    }
}
