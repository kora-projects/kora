package ru.tinkoff.kora.validation.annotation.processor;

import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;
import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.List;
import java.util.Set;

public final class ValidAnnotationProcessor extends AbstractKoraProcessor {

    private ValidatorGenerator generator;

    record ValidatorSpec(ValidMeta meta, TypeSpec spec, List<ParameterSpec> parameterSpecs) {}

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(ValidMeta.VALID_TYPE.canonicalName());
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.generator = new ValidatorGenerator(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        final List<TypeElement> validatedElements = getValidatedTypeElements(processingEnv, roundEnv);
        for (var validatedElement : validatedElements) {
            try {
                this.generator.generateFor(validatedElement);
            } catch (ProcessingErrorException e) {
                e.printError(this.processingEnv);
            }
        }


        return false;
    }

    private List<TypeElement> getValidatedTypeElements(ProcessingEnvironment processEnv, RoundEnvironment roundEnv) {
        final TypeElement annotation = processEnv.getElementUtils().getTypeElement(ValidMeta.VALID_TYPE.canonicalName());

        return roundEnv.getElementsAnnotatedWith(annotation).stream()
            .filter(a -> a instanceof TypeElement)
            .map(element -> {
                if (element.getKind() == ElementKind.ENUM) {
                    throw new ProcessingErrorException("Validation can't be generated for: " + element.getKind(), element);
                }
                if (element.getKind() == ElementKind.INTERFACE && !element.getModifiers().contains(Modifier.SEALED)) {
                    throw new ProcessingErrorException("Validation can't be generated for: " + element.getKind(), element);
                }
                return ((TypeElement) element);
            })
            .toList();
    }
}
