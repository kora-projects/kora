package io.koraframework.resilient.annotation.processor.aop;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.annotation.processor.common.MethodUtils;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.aop.annotation.processor.KoraAspect;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static com.palantir.javapoet.CodeBlock.joining;

public class FallbackKoraAspect implements KoraAspect {

    private static final ClassName ANNOTATION_TYPE = ClassName.get("io.koraframework.resilient.fallback.annotation", "Fallback");
    private static final ClassName FALLBACK_TELEMETRY = ClassName.get("io.koraframework.resilient.fallback.telemetry", "FallbackTelemetry");
    private static final ClassName FALLBACK_TELEMETRY_FACTORY = ClassName.get("io.koraframework.resilient.fallback.telemetry", "FallbackTelemetryFactory");
    private static final ClassName RESILIENT_CONFIG = ClassName.get("io.koraframework.resilient", "ResilientConfig");

    private final ProcessingEnvironment env;

    public FallbackKoraAspect(ProcessingEnvironment env) {
        this.env = env;
    }

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(ANNOTATION_TYPE);
    }

    @Override
    public ApplyResult apply(ExecutableElement method, String superCall, AspectContext aspectContext) {
        if (MethodUtils.isPublisher(method)) {
            throw new ProcessingErrorException("@%s can't be applied for type ".formatted(ANNOTATION_TYPE.simpleName()) + CommonClassNames.publisher, method);
        } else if (MethodUtils.isFuture(method) && !MethodUtils.isCompletionStage(method)) {
            throw new ProcessingErrorException("@%s can't be applied for type ".formatted(ANNOTATION_TYPE) + method.getReturnType(), method);
        }

        final Optional<? extends AnnotationMirror> mirror = method.getAnnotationMirrors().stream()
            .filter(a -> a.getAnnotationType().toString().equals(ANNOTATION_TYPE.canonicalName()))
            .findFirst();
        final FallbackMeta fallback = mirror.flatMap(a -> a.getElementValues().entrySet().stream()
                .filter(e -> e.getKey().getSimpleName().contentEquals("method"))
                .map(e -> String.valueOf(e.getValue().getValue()))
                .findFirst()
                .filter(v -> !v.isBlank()))
            .map(v -> FallbackMeta.ofFallbackMethod(v, method, env))
            .orElseThrow(() -> new IllegalStateException("Method argument for @Fallback is mandatory!"));

        var telemetryName = method.getEnclosingElement() + "." + method.getSimpleName();
        var fieldTelemetryFactory = aspectContext.fieldFactory().constructorParam(FALLBACK_TELEMETRY_FACTORY, List.of());
        var fieldResilientConfig = aspectContext.fieldFactory().constructorParam(RESILIENT_CONFIG, List.of());
        var fieldTelemetry = aspectContext.fieldFactory().constructorInitialized(
            FALLBACK_TELEMETRY,
            CodeBlock.of("$N.get($S, $N.fallback())", fieldTelemetryFactory, telemetryName, fieldResilientConfig)
        );

        final CodeBlock body = MethodUtils.isCompletionStage(method)
            ? buildBodyCompletableStage(method, fallback, superCall, fieldTelemetry)
            : buildBodySync(method, fallback, superCall, fieldTelemetry);

        return new ApplyResult.MethodBody(body);
    }

    private CodeBlock buildBodySync(ExecutableElement method, FallbackMeta fallbackCall, String superCall, String fieldTelemetry) {
        var reasonGuard = fallbackCall.hasReason()
            ? CodeBlock.of("if (!(_e instanceof $T)) {\n  throw _e;\n}\n", TypeName.get(fallbackCall.reasonType()))
            : CodeBlock.of("");
        final CodeBlock superMethod = buildMethodCall(method, superCall);
        if (MethodUtils.isVoid(method)) {
            return CodeBlock.builder().add("""
                try {
                    $L;
                } catch (Throwable _e) {
                    $L
                    var _fallbackObservation = $L.observe();
                    try {
                        _fallbackObservation.recordExecute(_e);
                        $L;
                    } catch (Throwable _fallbackException) {
                        _fallbackObservation.observeError(_fallbackException);
                        throw _fallbackException;
                    } finally {
                        _fallbackObservation.end();
                    }
                }
                """, superMethod, reasonGuard, fieldTelemetry, fallbackCall.call()).build();
        }
        return CodeBlock.builder().add("""
            try {
                return $L;
            } catch (Throwable _e) {
                $L
                var _fallbackObservation = $L.observe();
                try {
                    _fallbackObservation.recordExecute(_e);
                    return $L;
                } catch (Throwable _fallbackException) {
                    _fallbackObservation.observeError(_fallbackException);
                    throw _fallbackException;
                } finally {
                    _fallbackObservation.end();
                }
            }
            """, superMethod, reasonGuard, fieldTelemetry, fallbackCall.call()).build();
    }

    private CodeBlock buildBodyCompletableStage(ExecutableElement method, FallbackMeta fallbackCall, String superCall, String fieldTelemetry) {
        final CodeBlock superMethod = buildMethodCall(method, superCall);
        var reasonGuard = fallbackCall.hasReason()
            ? CodeBlock.of("if (!(_cause instanceof $T)) {\n  return $T.failedFuture(_cause);\n}\n", TypeName.get(fallbackCall.reasonType()), CompletableFuture.class)
            : CodeBlock.of("");

        return CodeBlock.builder().add("""
                return $L.exceptionallyCompose(_e -> {
                    var _cause = _e;
                    if (_cause instanceof $T ce) {
                        _cause = ce.getCause();
                    }
                    $L
                    var _fallbackObservation = $L.observe();
                    try {
                        _fallbackObservation.recordExecute(_cause);
                        return $L;
                    } catch (Throwable _fallbackException) {
                        _fallbackObservation.observeError(_fallbackException);
                        return $T.failedFuture(_fallbackException);
                    } finally {
                        _fallbackObservation.end();
                    }
                });""", superMethod, CompletionException.class, reasonGuard, fieldTelemetry, fallbackCall.call("_cause"), CompletableFuture.class)
            .build();
    }

    private CodeBlock buildMethodCall(ExecutableElement method, String call) {
        return method.getParameters().stream()
            .map(p -> CodeBlock.of("$L", p))
            .collect(joining(", ", call + "(", ")"));
    }
}
