package ru.tinkoff.kora.validation.annotation.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;
import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ru.tinkoff.kora.validation.annotation.processor.ValidTypes.VALID_TYPE;

public final class ValidAnnotationProcessor extends AbstractKoraProcessor {

    private ValidatorGenerator generator;

    record ValidatorSpec(ValidMeta meta, TypeSpec spec, List<ParameterSpec> parameterSpecs) {}

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(VALID_TYPE);
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.generator = new ValidatorGenerator(processingEnv);
    }

    @Override
    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        var validatedElements = annotatedElements.getOrDefault(VALID_TYPE, List.of());
        for (var annotated : validatedElements) {
            var element = annotated.element();
            if (element.getKind() == ElementKind.ENUM) {
                this.messager.printMessage(Diagnostic.Kind.ERROR, "Validation can't be generated for enum", element);
                continue;
            }
            if (element.getKind() == ElementKind.INTERFACE && !element.getModifiers().contains(Modifier.SEALED)) {
                this.messager.printMessage(Diagnostic.Kind.ERROR, "Validation can't be generated for non sealed interface", element);
                continue;
            }
            if (element instanceof TypeElement validatedElement) {
                try {
                    this.generator.generateFor(validatedElement);
                } catch (ProcessingErrorException e) {
                    e.printError(this.processingEnv);
                }
            }
        }
    }
}
