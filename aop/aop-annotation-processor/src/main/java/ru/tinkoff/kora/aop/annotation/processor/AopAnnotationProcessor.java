package ru.tinkoff.kora.aop.annotation.processor;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.ProcessingError;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.common.AopAnnotation;

import javax.annotation.Nullable;
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
    private final List<ProcessingError> errors = new ArrayList<>();
    private final Set<TypeElementWithEquals> classesToProcess = new HashSet<>();
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
        this.aspects = ServiceLoader.load(KoraAspectFactory.class, this.getClass().getClassLoader()).stream()
            .map(ServiceLoader.Provider::get)
            .<KoraAspect>mapMulti((factory, sink) -> factory.create(processingEnv).ifPresent(sink))
            .toList();

        this.aopProcessor = new AopProcessor( this.types, this.elements, this.aspects );

        var aspects = this.aspects.stream()
            .map(Object::getClass)
            .map(Class::getCanonicalName)
            .collect(Collectors.joining("\n\t", "\t", ""));
        log.debug("Discovered aspects:\n{}", aspects);

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

    private record TypeElementWithEquals(Types types, TypeElement te) {
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof TypeElementWithEquals that) {
                var type = this.te.asType();
                var thatType = that.te.asType();
                return this.types.isSameType(type, thatType);
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
        for (var element : elements) {
            var typeElement = this.findTypeElement(element);
            if (typeElement != null) {
                this.classesToProcess.add(new TypeElementWithEquals(this.types, typeElement));
            }
        }

        for (var error : this.errors) {
            error.print(this.processingEnv);
        }
        if (!this.errors.isEmpty()) {
            return false;
        }
        var processedClasses = new ArrayList<TypeElementWithEquals>();
        for (var ctp : this.classesToProcess) {
            var te = ctp.te();
            log.info("Processing type {} with aspects", te);
            TypeSpec typeSpec;
            try {
                typeSpec = this.aopProcessor.applyAspects(te);
            } catch (ProcessingErrorException e) {
                e.printError(this.processingEnv);
                continue;
            }

            var packageElement = this.elements.getPackageOf(te);
            var javaFile = JavaFile.builder(packageElement.getQualifiedName().toString(), typeSpec).build();
            try {
                javaFile.writeTo(this.processingEnv.getFiler());
                processedClasses.add(ctp);
            } catch (IOException e) {
                this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Error on writing file: " + e.getMessage(), te);
            }
        }
        processedClasses.forEach(this.classesToProcess::remove);
        return false;
    }

    @Nullable
    private TypeElement findTypeElement(Element element) {
        if (element.getKind() == ElementKind.INTERFACE) {
            return null;
        }
        if (element.getKind() == ElementKind.CLASS) {
            if (element.getModifiers().contains(Modifier.ABSTRACT)) {
                return null;
            }
            if (element.getModifiers().contains(Modifier.FINAL)) {
                this.errors.add(new ProcessingError("Aspects can be applied only to non final classes, but " + element.getSimpleName() + " is final", element));
                return null;
            }
            var typeElement = (TypeElement) element;
            var constructor = AopUtils.findAopConstructor(typeElement);
            if (constructor == null) {
                this.errors.add(new ProcessingError("Can't find constructor suitable for aop proxy for " + element.getSimpleName(), element));
                return null;
            }
            return typeElement;
        }
        if (element.getKind() == ElementKind.PARAMETER) {
            return this.findTypeElement(element.getEnclosingElement());
        }
        if (element.getKind() != ElementKind.METHOD) {
            this.errors.add(new ProcessingError("Aspects can be applied only to classes or methods, got %s".formatted(element.getKind()), element));
            return null;
        }
        if (element.getModifiers().contains(Modifier.FINAL) || element.getModifiers().contains(Modifier.PRIVATE)) {
            this.errors.add(new ProcessingError("Aspects can be applied only to non final non private methods", element));
            return null;
        }
        return this.findTypeElement(element.getEnclosingElement());
    }
}
