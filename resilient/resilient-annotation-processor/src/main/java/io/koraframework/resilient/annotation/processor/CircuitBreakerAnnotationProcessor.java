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

    private static final ClassName KORA_RETRY_BUDGET = ClassName.get("io.koraframework.resilient.retry", "KoraRetryBudget");
    private static final ClassName RESILIENT_CONFIG = ClassName.get("io.koraframework.resilient", "ResilientConfig");
    private static final ClassName RETRY = ClassName.get("io.koraframework.resilient.retry", "Retry");

    private static final List<Spec> SPECS = List.of(
        new Spec(
            ClassName.get("io.koraframework.resilient.circuitbreaker.annotation", "CircuitBreakerSpec"),
            ClassName.get("io.koraframework.resilient.circuitbreaker", "CircuitBreaker"),
            ClassName.get("io.koraframework.resilient.circuitbreaker", "KoraCircuitBreaker"),
            ClassName.get("io.koraframework.resilient.circuitbreaker", "CircuitBreakerConfig"),
            ClassName.get("io.koraframework.resilient.circuitbreaker", "CircuitBreakerPredicate"),
            ClassName.get("io.koraframework.resilient.circuitbreaker.telemetry", "CircuitBreakerTelemetryFactory"),
            ClassName.get("io.koraframework.resilient.circuitbreaker.telemetry", "CircuitBreakerTelemetryConfig"),
            ClassName.get("io.koraframework.resilient.circuitbreaker.telemetry", "CircuitBreakerOperationTelemetryConfig"),
            "circuitBreaker"
        ),
        new Spec(
            ClassName.get("io.koraframework.resilient.retry.annotation", "RetrySpec"),
            RETRY,
            ClassName.get("io.koraframework.resilient.retry", "KoraRetry"),
            ClassName.get("io.koraframework.resilient.retry", "RetryConfig"),
            ClassName.get("io.koraframework.resilient.retry", "RetryPredicate"),
            ClassName.get("io.koraframework.resilient.retry.telemetry", "RetryTelemetryFactory"),
            ClassName.get("io.koraframework.resilient.retry.telemetry", "RetryTelemetryConfig"),
            ClassName.get("io.koraframework.resilient.retry.telemetry", "RetryOperationTelemetryConfig"),
            "retry"
        ),
        new Spec(
            ClassName.get("io.koraframework.resilient.timeout.annotation", "TimeoutSpec"),
            ClassName.get("io.koraframework.resilient.timeout", "Timeouter"),
            ClassName.get("io.koraframework.resilient.timeout", "KoraTimeouter"),
            ClassName.get("io.koraframework.resilient.timeout", "TimeoutConfig"),
            null,
            ClassName.get("io.koraframework.resilient.timeout.telemetry", "TimeoutTelemetryFactory"),
            ClassName.get("io.koraframework.resilient.timeout.telemetry", "TimeoutTelemetryConfig"),
            ClassName.get("io.koraframework.resilient.timeout.telemetry", "TimeoutOperationTelemetryConfig"),
            "timeout"
        ),
        new Spec(
            ClassName.get("io.koraframework.resilient.ratelimiter.annotation", "RateLimiterSpec"),
            ClassName.get("io.koraframework.resilient.ratelimiter", "RateLimiter"),
            ClassName.get("io.koraframework.resilient.ratelimiter", "KoraRateLimiter"),
            ClassName.get("io.koraframework.resilient.ratelimiter", "RateLimiterConfig"),
            null,
            ClassName.get("io.koraframework.resilient.ratelimiter.telemetry", "RateLimiterTelemetryFactory"),
            ClassName.get("io.koraframework.resilient.ratelimiter.telemetry", "RateLimiterTelemetryConfig"),
            ClassName.get("io.koraframework.resilient.ratelimiter.telemetry", "RateLimiterOperationTelemetryConfig"),
            "rateLimiter"
        )
    );

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return SPECS.stream().map(Spec::annotation).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    protected void process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv,
                           java.util.Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        for (var spec : SPECS) {
            for (var annotatedElement : annotatedElements.getOrDefault(spec.annotation(), List.of())) {
                if (!(annotatedElement.element() instanceof TypeElement resilientType)) {
                    continue;
                }
                validate(resilientType, spec);
                var annotation = AnnotationUtils.findAnnotation(resilientType, spec.annotation());
                if (annotation == null) {
                    continue;
                }
                var configPath = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(annotation, "value");
                if (configPath == null) {
                    continue;
                }
                if (configPath.isBlank()) {
                    throw new ProcessingErrorException("@%s config path can't be blank".formatted(spec.annotation().simpleName()), resilientType);
                }

                generateImplementation(resilientType, spec, configPath);
                generateModule(resilientType, spec, configPath);
            }
        }
    }

    private void validate(TypeElement type, Spec spec) {
        if (!type.getKind().isInterface()) {
            throw new ProcessingErrorException("@%s is intended to be used on interfaces, but was: %s"
                .formatted(spec.annotation().simpleName(), type.getKind().name()), type);
        }
        TypeMirror contract = processingEnv.getElementUtils().getTypeElement(spec.contract().canonicalName()).asType();
        if (!processingEnv.getTypeUtils().isAssignable(type.asType(), contract)) {
            throw new ProcessingErrorException("@%s annotated interface must extend %s"
                .formatted(spec.annotation().simpleName(), spec.contract().canonicalName()), type);
        }
    }

    private void generateImplementation(TypeElement resilientType, Spec spec, String configPath) {
        var impl = implementationName(resilientType);
        var simpleName = resilientType.getSimpleName().toString();
        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(spec.config(), "config");
        if (spec.predicate() != null) {
            constructor.addParameter(ParameterSpec.builder(spec.predicate(), "failurePredicate")
                .addAnnotation(Nullable.class)
                .build());
        }
        constructor.addParameter(spec.telemetryFactory(), "telemetryFactory");
        constructor.addParameter(spec.telemetryConfig(), "telemetryConfig");

        if (spec.contract().equals(RETRY)) {
            constructor.addStatement("super($S, config, failurePredicate, retryBudget(config), telemetryFactory.get(CONFIG_PATH, telemetryConfig))", simpleName);
        } else if (spec.contract().canonicalName().equals("io.koraframework.resilient.circuitbreaker.CircuitBreaker")) {
            constructor.addStatement("super($S, config, failurePredicate, telemetryFactory.get(CONFIG_PATH, telemetryConfig))", simpleName);
        } else if (spec.contract().canonicalName().equals("io.koraframework.resilient.timeout.Timeouter")) {
            constructor.addStatement("super($S, config.duration(), telemetryFactory.get(CONFIG_PATH, telemetryConfig), config)", simpleName);
        } else {
            constructor.addStatement("super($S, config, telemetryFactory.get(CONFIG_PATH, telemetryConfig))", simpleName);
        }

        var type = TypeSpec.classBuilder(impl)
            .addOriginatingElement(resilientType)
            .addAnnotation(AnnotationUtils.generated(CircuitBreakerAnnotationProcessor.class))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .superclass(spec.baseImplementation())
            .addSuperinterface(TypeName.get(resilientType.asType()))
            .addField(FieldSpec.builder(String.class, "CONFIG_PATH", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", configPath)
                .build())
            .addMethod(constructor.build());

        if (spec.contract().equals(RETRY)) {
            type.addMethod(MethodSpec.methodBuilder("retryBudget")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .addAnnotation(Nullable.class)
                .returns(KORA_RETRY_BUDGET)
                .addParameter(spec.config(), "config")
                .addCode("""
                    var retryBudget = config.retryBudget();
                    if (retryBudget == null || !retryBudget.enabled()) {
                        return null;
                    }
                    return new $T(
                        retryBudget.ratio(),
                        retryBudget.tokensMax(),
                        retryBudget.tokensInitial(),
                        retryBudget.minTokensPerSecond()
                    );
                    """, KORA_RETRY_BUDGET)
                .build());
        }

        try {
            JavaFile.builder(impl.packageName(), type.build()).build().writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void generateModule(TypeElement resilientType, Spec spec, String configPath) {
        var contract = ClassName.get(resilientType);
        var impl = implementationName(resilientType);
        var module = ClassName.get(contract.packageName(), NameUtils.generatedType(resilientType, "Module"));
        var methodPrefix = CommonUtils.decapitalize(NameUtils.getOuterClassesAsPrefix(resilientType).substring(1) + resilientType.getSimpleName());
        var mapperType = ParameterizedTypeName.get(CommonClassNames.configValueMapper, spec.config());

        var implMethod = MethodSpec.methodBuilder(methodPrefix + "_Impl")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addParameter(ParameterSpec.builder(spec.config(), "config")
                .addAnnotation(TagUtils.makeAnnotationSpec(contract))
                .build())
            .addParameter(spec.telemetryFactory(), "telemetryFactory")
            .addParameter(RESILIENT_CONFIG, "resilientConfig")
            .returns(TypeName.get(resilientType.asType()));
        implMethod.addStatement("var telemetryConfig = new $T(resilientConfig.$L(), config.telemetry())", spec.operationTelemetryConfig(), spec.telemetryAccessor());
        if (spec.predicate() != null) {
            implMethod.addParameter(ParameterSpec.builder(spec.predicate(), "failurePredicate")
                .addAnnotation(TagUtils.makeAnnotationSpec(contract))
                .addAnnotation(Nullable.class)
                .build());
            implMethod.addStatement("return new $T(config, failurePredicate, telemetryFactory, telemetryConfig)", impl);
        } else {
            implMethod.addStatement("return new $T(config, telemetryFactory, telemetryConfig)", impl);
        }

        var type = TypeSpec.interfaceBuilder(module)
            .addOriginatingElement(resilientType)
            .addAnnotation(AnnotationUtils.generated(CircuitBreakerAnnotationProcessor.class))
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(CommonClassNames.module)
            .addMethod(MethodSpec.methodBuilder(methodPrefix + "_Config")
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                .addAnnotation(TagUtils.makeAnnotationSpec(contract))
                .addParameter(CommonClassNames.config, "config")
                .addParameter(mapperType, "mapper")
                .returns(spec.config())
                .addStatement("return mapper.mapOrThrow(config.get($S))", configPath)
                .build())
            .addMethod(implMethod.build())
            .build();

        try {
            JavaFile.builder(module.packageName(), type).build().writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static ClassName implementationName(TypeElement resilientType) {
        var contract = ClassName.get(resilientType);
        return ClassName.get(contract.packageName(), NameUtils.generatedType(resilientType, "Impl"));
    }

    private record Spec(
        ClassName annotation,
        ClassName contract,
        ClassName baseImplementation,
        ClassName config,
        @Nullable ClassName predicate,
        ClassName telemetryFactory,
        ClassName telemetryConfig,
        ClassName operationTelemetryConfig,
        String telemetryAccessor
    ) {}
}
