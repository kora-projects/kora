package io.koraframework.resilient.annotation.processor;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.*;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.util.List;
import java.util.Set;

public class CircuitBreakerAnnotationProcessor extends AbstractKoraProcessor {

    private static final ClassName ANNOTATION_TYPE = ClassName.get("io.koraframework.resilient.circuitbreaker.annotation", "CircuitBreaker");
    private static final ClassName CIRCUIT_BREAKER = ClassName.get("io.koraframework.resilient.circuitbreaker", "CircuitBreaker");
    private static final ClassName KORA_CIRCUIT_BREAKER = ClassName.get("io.koraframework.resilient.circuitbreaker", "KoraCircuitBreaker");
    private static final ClassName CIRCUIT_BREAKER_CONFIG = ClassName.get("io.koraframework.resilient.circuitbreaker", "CircuitBreakerConfig");
    private static final ClassName CIRCUIT_BREAKER_PREDICATE = ClassName.get("io.koraframework.resilient.circuitbreaker", "CircuitBreakerPredicate");
    private static final ClassName CIRCUIT_BREAKER_TELEMETRY_FACTORY = ClassName.get("io.koraframework.resilient.circuitbreaker.telemetry", "CircuitBreakerTelemetryFactory");

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(ANNOTATION_TYPE);
    }

    @Override
    protected void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, java.util.Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        for (var annotatedElement : annotatedElements.getOrDefault(ANNOTATION_TYPE, List.of())) {
            if (!(annotatedElement.element() instanceof TypeElement circuitBreakerType)) {
                continue;
            }
            validate(circuitBreakerType);

            var annotation = AnnotationUtils.findAnnotation(circuitBreakerType, ANNOTATION_TYPE);
            if (annotation == null) {
                continue;
            }
            var configPath = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(annotation, "value");
            if (configPath == null) {
                continue;
            }
            validate(circuitBreakerType, configPath);

            generateImplementation(circuitBreakerType, configPath);
            generateModule(circuitBreakerType, configPath);
        }
    }

    private void validate(TypeElement type) {
        if (!type.getKind().isInterface()) {
            throw new ProcessingErrorException("@%s is intended to be used on interfaces, but was: %s"
                .formatted(ANNOTATION_TYPE.simpleName(), type.getKind().name()), type);
        }

        TypeMirror circuitBreaker = processingEnv.getElementUtils().getTypeElement(CIRCUIT_BREAKER.canonicalName()).asType();
        if (!processingEnv.getTypeUtils().isAssignable(type.asType(), circuitBreaker)) {
            throw new ProcessingErrorException("@%s annotated interface must extend %s"
                .formatted(ANNOTATION_TYPE.simpleName(), CIRCUIT_BREAKER.canonicalName()), type);
        }
    }

    private static void validate(TypeElement type, String configPath) {
        if (configPath.isBlank()) {
            throw new ProcessingErrorException("@%s config path can't be blank".formatted(ANNOTATION_TYPE.simpleName()), type);
        }
    }

    private void generateImplementation(TypeElement circuitBreakerType, String configPath) {
        var impl = implementationName(circuitBreakerType);
        var type = TypeSpec.classBuilder(impl)
            .addOriginatingElement(circuitBreakerType)
            .addAnnotation(AnnotationUtils.generated(CircuitBreakerAnnotationProcessor.class))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .superclass(KORA_CIRCUIT_BREAKER)
            .addSuperinterface(TypeName.get(circuitBreakerType.asType()))
            .addField(FieldSpec.builder(String.class, "CONFIG_PATH", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", configPath)
                .build())
            .addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(String.class, "name")
                .addParameter(CIRCUIT_BREAKER_CONFIG, "config")
                .addParameter(ParameterSpec.builder(CIRCUIT_BREAKER_PREDICATE, "failurePredicate")
                    .addAnnotation(Nullable.class)
                    .build())
                .addParameter(CIRCUIT_BREAKER_TELEMETRY_FACTORY, "telemetryFactory")
                .addStatement("super(name, config, failurePredicate, telemetryFactory.get(CONFIG_PATH, config.telemetry()))")
                .build())
            .build();

        try {
            JavaFile.builder(impl.packageName(), type).build().writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void generateModule(TypeElement circuitBreakerType, String configPath) {
        var contract = ClassName.get(circuitBreakerType);
        var impl = implementationName(circuitBreakerType);
        var module = ClassName.get(contract.packageName(), NameUtils.generatedType(circuitBreakerType, "Module"));
        var methodPrefix = CommonUtils.decapitalize(NameUtils.getOuterClassesAsPrefix(circuitBreakerType).substring(1) + circuitBreakerType.getSimpleName());
        var mapperType = ParameterizedTypeName.get(CommonClassNames.configValueMapper, CIRCUIT_BREAKER_CONFIG);

        var type = TypeSpec.interfaceBuilder(module)
            .addOriginatingElement(circuitBreakerType)
            .addAnnotation(AnnotationUtils.generated(CircuitBreakerAnnotationProcessor.class))
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(CommonClassNames.module)
            .addMethod(MethodSpec.methodBuilder(methodPrefix + "_Config")
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                .addAnnotation(TagUtils.makeAnnotationSpec(contract))
                .addParameter(CommonClassNames.config, "config")
                .addParameter(mapperType, "mapper")
                .returns(CIRCUIT_BREAKER_CONFIG)
                .addStatement("return mapper.mapOrThrow(config.get($S))", configPath)
                .build())
            .addMethod(MethodSpec.methodBuilder(methodPrefix + "_Impl")
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                .addParameter(ParameterSpec.builder(CIRCUIT_BREAKER_CONFIG, "config")
                    .addAnnotation(TagUtils.makeAnnotationSpec(contract))
                    .build())
                .addParameter(ParameterSpec.builder(CIRCUIT_BREAKER_PREDICATE, "failurePredicate")
                    .addAnnotation(TagUtils.makeAnnotationSpec(contract))
                    .addAnnotation(Nullable.class)
                    .build())
                .addParameter(CIRCUIT_BREAKER_TELEMETRY_FACTORY, "telemetryFactory")
                .returns(TypeName.get(circuitBreakerType.asType()))
                .addStatement("return new $T($S, config, failurePredicate, telemetryFactory)", impl, circuitBreakerType.getSimpleName().toString())
                .build())
            .build();

        try {
            JavaFile.builder(module.packageName(), type).build().writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static ClassName implementationName(TypeElement circuitBreakerType) {
        var contract = ClassName.get(circuitBreakerType);
        return ClassName.get(contract.packageName(), NameUtils.generatedType(circuitBreakerType, "Impl"));
    }
}
