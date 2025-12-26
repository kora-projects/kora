package ru.tinkoff.kora.annotation.processor.common;

import com.palantir.javapoet.ClassName;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.*;
import java.util.stream.Collectors;

public abstract class AbstractKoraProcessor extends AbstractProcessor {
    protected Types types;
    protected Elements elements;
    protected Messager messager;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        SourceVersion latestVersion = SourceVersion.latest();
        if (latestVersion.ordinal() >= 17) {
            return latestVersion;
        }
        return SourceVersion.RELEASE_17;
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        BuildEnvironment.init(processingEnv);
        this.types = processingEnv.getTypeUtils();
        this.elements = processingEnv.getElementUtils();
        this.messager = processingEnv.getMessager();
    }

    @Override
    public final Set<String> getSupportedAnnotationTypes() {
        return getSupportedAnnotationClassNames().stream().map(ClassName::canonicalName).collect(Collectors.toSet());
    }

    public record AnnotatedElement(TypeElement annotationType, Element element) {}

    @Override
    public final boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        var start = System.currentTimeMillis();
        var supportedAnnotationNames = getSupportedAnnotationClassNames();
        var annotatedElements = new HashMap<ClassName, List<AnnotatedElement>>();
        var annotatedElementsSize = 0;
        for (var annotation : annotations) {
            var annotationClassName = ClassName.get(annotation);
            if (supportedAnnotationNames.contains(annotationClassName)) {
                for (var element : roundEnv.getElementsAnnotatedWith(annotation)) {
                    annotatedElements.computeIfAbsent(annotationClassName, n -> new ArrayList<>()).add(new AnnotatedElement(annotation, element));
                    annotatedElementsSize++;
                }
            }
        }
        try {
            this.process(annotations, roundEnv, annotatedElements);
        } catch (ProcessingErrorException e) {
            e.printError(this.processingEnv);
        }
        var end = System.currentTimeMillis();
        var took = end - start;
        if (took > 100) {
            this.messager.printMessage(Diagnostic.Kind.NOTE, "%s processing took %sms for %d elements".formatted(this.getClass().getSimpleName(), took, annotatedElementsSize));
        }
        return false;
    }

    protected abstract void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements);

    public abstract Set<ClassName> getSupportedAnnotationClassNames();

    @Nullable
    protected TypeElement findRoundAnnotation(Set<? extends TypeElement> roundAnnotation, ClassName className) {
        for (var typeElement : roundAnnotation) {
            if (typeElement.getQualifiedName().contentEquals(className.canonicalName())) {
                return typeElement;
            }
        }
        return null;
    }
}
