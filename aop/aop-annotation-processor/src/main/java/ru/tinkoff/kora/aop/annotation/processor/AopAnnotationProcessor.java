package ru.tinkoff.kora.aop.annotation.processor;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.TypeSpec;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import ru.tinkoff.kora.annotation.processor.common.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class AopAnnotationProcessor extends AbstractKoraProcessor {
    private static final Logger log = LoggerFactory.getLogger(AopAnnotationProcessor.class);
    private List<KoraAspect> aspects;
    private Set<ClassName> annotations;
    private AopProcessor aopProcessor;

    public AopAnnotationProcessor() {
    }

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return this.annotations;
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.aspects = ServiceLoader.load(KoraAspectFactory.class, this.getClass().getClassLoader()).stream()
            .map(ServiceLoader.Provider::get)
            .<KoraAspect>mapMulti((factory, sink) -> factory.create(processingEnv).ifPresent(sink))
            .toList();
        this.aopProcessor = new AopProcessor(this.types, this.elements, this.aspects);
        this.annotations = this.aspects.stream()
            .flatMap(a -> a.getSupportedAnnotationClassNames().stream())
            .collect(Collectors.toSet());

        if (log.isDebugEnabled()) {
            var aspects = this.aspects.stream()
                .map(Object::getClass)
                .map(Class::getCanonicalName)
                .collect(Collectors.joining("\n\t", "\t", "")).indent(4);
            log.debug("Discovered aspects:\n{}", aspects);
        }

    }

    @Override
    protected void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        record RoundAnnotations(List<? extends TypeElement> withAopAnnotation, List<? extends TypeElement> noAopAnnotation) {}

        var roundAnnotations = annotations.stream()
            .filter(a -> this.annotations.contains(ClassName.get(a)))
            .collect(Collectors.teeing(
                Collectors.filtering(a -> AnnotationUtils.isAnnotationPresent(a, CommonClassNames.aopAnnotation), Collectors.toList()),
                Collectors.filtering(a -> !AnnotationUtils.isAnnotationPresent(a, CommonClassNames.aopAnnotation), Collectors.toList()),
                RoundAnnotations::new
            ));
        for (var typeElement : roundAnnotations.noAopAnnotation()) {
            log.warn("Annotation {} has no @AopAnnotation marker, it will not be handled by some util methods", typeElement.getSimpleName());
        }

        var elements = roundEnv.getElementsAnnotatedWithAny(roundAnnotations.withAopAnnotation().toArray(TypeElement[]::new));
        var classesToProcess = new HashMap<ClassName, TypeElement>();

        for (var element : elements) {
            var typeElement = this.findTypeElement(element);
            if (typeElement.isRight()) {
                typeElement.right().print(processingEnv);
            } else if (typeElement.isLeft() && typeElement.left() != null) {
                classesToProcess.put(ClassName.get(typeElement.left()), typeElement.left());
            }
        }

        if (!classesToProcess.isEmpty()) {
            if (log.isInfoEnabled()) {
                LogUtils.logElementsFull(log, Level.INFO, "Components with aspects found", classesToProcess.values());
            }
        }

        for (var te : classesToProcess.values()) {
            TypeSpec typeSpec;
            try {
                typeSpec = this.aopProcessor.applyAspects(te);
            } catch (ProcessingErrorException e) {
                e.printError(this.processingEnv);
                continue;
            }

            var packageElement = this.elements.getPackageOf(te);
            var javaFile = JavaFile.builder(packageElement.getQualifiedName().toString(), typeSpec.toBuilder().addOriginatingElement(te).build()).build();
            try {
                javaFile.writeTo(this.processingEnv.getFiler());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Nullable
    private Either<TypeElement, ProcessingError> findTypeElement(Element element) {
        if (element.getKind() == ElementKind.INTERFACE) {
            return Either.left(null);
        }
        if (element.getKind() == ElementKind.CLASS) {
            if (element.getModifiers().contains(Modifier.ABSTRACT)) {
                return Either.left(null);
            }
            if (element.getModifiers().contains(Modifier.FINAL)) {
                return Either.right(new ProcessingError("Aspects can't be applied to final classes, but " + element.getSimpleName() + " is final", element));
            }
            var typeElement = (TypeElement) element;
            var constructor = AopUtils.findAopConstructor(typeElement);
            if (constructor == null) {
                return Either.right(new ProcessingError("Can't find constructor suitable for aop proxy for " + element.getSimpleName(), element));
            }
            return Either.left(typeElement);
        }
        if (element.getKind() == ElementKind.PARAMETER) {
            return this.findTypeElement(element.getEnclosingElement());
        }
        if (element.getKind() != ElementKind.METHOD) {
            return Either.right(new ProcessingError("Aspects can be applied only to classes or methods, got %s".formatted(element.getKind()), element));
        }
        if (element.getModifiers().contains(Modifier.FINAL)) {
            return Either.right(new ProcessingError("Aspects can't be applied to final methods, but method " + element.getEnclosingElement().getSimpleName() + "#" + element.getSimpleName() + "() is final", element));
        }
        if (element.getModifiers().contains(Modifier.PRIVATE)) {
            return Either.right(new ProcessingError("Aspects can't be applied to private methods, but method " + element.getEnclosingElement().getSimpleName() + "#" + element.getSimpleName() + "() is private", element));
        }
        return this.findTypeElement(element.getEnclosingElement());
    }
}
