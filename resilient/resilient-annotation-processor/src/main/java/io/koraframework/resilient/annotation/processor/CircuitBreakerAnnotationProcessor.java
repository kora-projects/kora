package io.koraframework.resilient.annotation.processor;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.*;

import javax.annotation.processing.FilerException;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CircuitBreakerAnnotationProcessor extends AbstractKoraProcessor {

    private static final ClassName ANNOTATION_TYPE = ClassName.get("io.koraframework.resilient.circuitbreaker.annotation", "CircuitBreaker");
    private static final ClassName CIRCUIT_BREAKER_FACTORY_MODULE = ClassName.get("io.koraframework.resilient.circuitbreaker", "CircuitBreakerFactoryModule");

    private final Map<String, String> generatedConfigPathByTag = new LinkedHashMap<>();
    private final Map<String, ExecutableElement> generatedByConfigPath = new LinkedHashMap<>();

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(ANNOTATION_TYPE);
    }

    @Override
    protected void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        var configPaths = new LinkedHashMap<String, ExecutableElement>();
        for (var annotatedElement : annotatedElements.getOrDefault(ANNOTATION_TYPE, List.of())) {
            if (!(annotatedElement.element() instanceof ExecutableElement method)) {
                continue;
            }

            var annotation = AnnotationUtils.findAnnotation(method, ANNOTATION_TYPE);
            if (annotation == null) {
                continue;
            }

            var configPath = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(annotation, "value");
            if (configPath == null) {
                continue;
            }

            validate(method, configPath);
            validateGeneratedNameCollision(method, configPath);

            configPaths.putIfAbsent(configPath, method);
        }

        for (var entry : configPaths.entrySet()) {
            var configPath = entry.getKey();
            var method = entry.getValue();
            if (this.generatedByConfigPath.putIfAbsent(configPath, method) != null) {
                continue;
            }

            var tag = CircuitBreakerTagUtils.tagName(configPath);
            var module = CircuitBreakerTagUtils.moduleName(configPath);
            if (!isAlreadyGeneratedTag(method, tag, configPath)) {
                generateTag(method, tag, configPath);
            }
            if (!isAlreadyGeneratedModule(module)) {
                generateModule(method, tag, module, configPath);
            }
        }
    }

    private static void validate(ExecutableElement method, String configPath) {
        if (configPath.isBlank()) {
            throw new ProcessingErrorException("@%s config path can't be blank".formatted(ANNOTATION_TYPE.simpleName()), method);
        }
        if (CircuitBreakerTagUtils.isReservedPath(configPath)) {
            throw new ProcessingErrorException("@%s config path '%s' is reserved".formatted(ANNOTATION_TYPE.simpleName(), configPath), method);
        }
    }

    private void validateGeneratedNameCollision(ExecutableElement method, String configPath) {
        var tagName = CircuitBreakerTagUtils.tagName(configPath).canonicalName();
        var previousPath = this.generatedConfigPathByTag.putIfAbsent(tagName, configPath);
        if (previousPath != null && !previousPath.equals(configPath)) {
            throw new ProcessingErrorException(
                "@%s config paths '%s' and '%s' generate the same tag '%s'; use paths with different alphanumeric names"
                    .formatted(ANNOTATION_TYPE.simpleName(), previousPath, configPath, tagName),
                method
            );
        }
    }

    private boolean isAlreadyGeneratedTag(ExecutableElement method, ClassName tag, String configPath) {
        var existing = this.processingEnv.getElementUtils().getTypeElement(tag.canonicalName());
        if (existing == null) {
            return false;
        }

        var existingConfigPath = existing.getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.FIELD)
            .filter(e -> e.getSimpleName().contentEquals(CircuitBreakerTagUtils.CONFIG_PATH_FIELD))
            .map(VariableElement.class::cast)
            .map(VariableElement::getConstantValue)
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .findFirst()
            .orElse(null);

        if (existingConfigPath != null && !existingConfigPath.equals(configPath)) {
            throw new ProcessingErrorException(
                "@%s config path '%s' generated tag '%s' that already exists for config path '%s'"
                    .formatted(ANNOTATION_TYPE.simpleName(), configPath, tag.canonicalName(), existingConfigPath),
                method
            );
        }
        return true;
    }

    private boolean isAlreadyGeneratedModule(ClassName module) {
        return this.processingEnv.getElementUtils().getTypeElement(module.canonicalName()) != null;
    }

    private void generateTag(ExecutableElement method, ClassName tag, String configPath) {
        var type = TypeSpec.annotationBuilder(tag)
            .addOriginatingElement(method)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationUtils.generated(CircuitBreakerAnnotationProcessor.class))
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.tag)
                .addMember("value", "$T.class", tag)
                .build())
            .addField(FieldSpec.builder(String.class, CircuitBreakerTagUtils.CONFIG_PATH_FIELD, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", configPath)
                .build())
            .build();

        try {
            JavaFile.builder(tag.packageName(), type).build().writeTo(processingEnv.getFiler());
        } catch (FilerException e) {
            if (this.processingEnv.getElementUtils().getTypeElement(tag.canonicalName()) == null) {
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void generateModule(ExecutableElement method, ClassName tag, ClassName module, String configPath) {
        var type = TypeSpec.interfaceBuilder(module)
            .addOriginatingElement(method)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationUtils.generated(CircuitBreakerAnnotationProcessor.class))
            .addAnnotation(CommonClassNames.module)
            .addMethod(MethodSpec.methodBuilder(CircuitBreakerTagUtils.factoryMethodName(configPath))
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                .addAnnotation(CommonClassNames.factoryModule)
                .addAnnotation(TagUtils.makeAnnotationSpec(tag))
                .returns(CIRCUIT_BREAKER_FACTORY_MODULE)
                .addStatement("return new $T($T.$L)", CIRCUIT_BREAKER_FACTORY_MODULE, tag, CircuitBreakerTagUtils.CONFIG_PATH_FIELD)
                .build())
            .build();

        try {
            JavaFile.builder(module.packageName(), type).build().writeTo(processingEnv.getFiler());
        } catch (FilerException e) {
            if (this.processingEnv.getElementUtils().getTypeElement(module.canonicalName()) == null) {
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
