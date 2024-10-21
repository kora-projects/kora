package ru.tinkoff.kora.aop.annotation.processor;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.LogUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingError;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.common.AopAnnotation;
import ru.tinkoff.kora.common.util.Either;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class AopAnnotationProcessor extends AbstractKoraProcessor {
    private static final Logger log = LoggerFactory.getLogger(AopAnnotationProcessor.class);
    private List<KoraAspect> aspects;
    private TypeElement[] annotations;
    private AopProcessor aopProcessor;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return this.aspects.stream()
            .<String>mapMulti((a, sink) -> a.getSupportedAnnotationTypes().forEach(sink))
            .collect(Collectors.toSet());
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        var aopAnnotation = processingEnv.getElementUtils().getTypeElement("ru.tinkoff.kora.common.AopAnnotation");
        if (aopAnnotation == null) {
            this.annotations = new TypeElement[0];
            this.aspects = List.of();
            return;
        }
        this.aspects = ServiceLoader.load(KoraAspectFactory.class, this.getClass().getClassLoader()).stream()
            .map(ServiceLoader.Provider::get)
            .<KoraAspect>mapMulti((factory, sink) -> factory.create(processingEnv).ifPresent(sink))
            .toList();
        if (this.aspects.isEmpty()) {
            this.annotations = new TypeElement[0];
            return;
        }

        this.aopProcessor = new AopProcessor(this.types, this.elements, this.aspects);

        if (log.isDebugEnabled()) {
            var aspects = this.aspects.stream()
                .map(Object::getClass)
                .map(Class::getCanonicalName)
                .collect(Collectors.joining("\n\t", "\t", "")).indent(4);
            log.debug("Discovered aspects:\n{}", aspects);
        }

        this.annotations = this.aspects.stream()
            .<String>mapMulti((a, sink) -> a.getSupportedAnnotationTypes().forEach(sink))
            .map(c -> this.elements.getTypeElement(c))
            .filter(Objects::nonNull)
            .toArray(TypeElement[]::new);

        var noAopAnnotation = Arrays.stream(this.annotations)
            .filter(a -> a.getAnnotation(AopAnnotation.class) == null)
            .toList();
        for (var typeElement : noAopAnnotation) {
            log.warn("Annotation {} has no @AopAnnotation marker, it will not be handled by some util methods", typeElement.getSimpleName());
        }
    }

    private record TypeElementWithEquals(TypeElement te) {
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof TypeElementWithEquals that) {
                return this.te.getQualifiedName().contentEquals(that.te.getQualifiedName());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return this.te.getQualifiedName().hashCode();
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        var elements = roundEnv.getElementsAnnotatedWithAny(this.annotations);
        var classesToProcess = new HashSet<TypeElementWithEquals>();

        for (var element : elements) {
            var typeElement = this.findTypeElement(element);
            if (typeElement.isRight()) {
                typeElement.right().print(processingEnv);
            } else if (typeElement.isLeft() && typeElement.left() != null) {
                classesToProcess.add(new TypeElementWithEquals(typeElement.left()));
            }
        }

        if (!classesToProcess.isEmpty()) {
            if (log.isInfoEnabled()) {
                var elems = classesToProcess.stream()
                    .map(c -> c.te)
                    .toList();
                LogUtils.logElementsFull(log, Level.INFO, "Components with aspects found", elems);
            }
        }

        for (var ctp : classesToProcess) {
            var te = ctp.te();
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
        return false;
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
