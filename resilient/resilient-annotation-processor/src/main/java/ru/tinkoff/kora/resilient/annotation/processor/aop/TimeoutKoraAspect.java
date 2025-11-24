package ru.tinkoff.kora.resilient.annotation.processor.aop;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.MethodUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.aop.annotation.processor.KoraAspect;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.palantir.javapoet.CodeBlock.joining;

public class TimeoutKoraAspect implements KoraAspect {

    private static final ClassName ANNOTATION_TYPE = ClassName.get("ru.tinkoff.kora.resilient.timeout.annotation", "Timeout");
    private static final ClassName EXHAUSTED_EXCEPTION = ClassName.get("ru.tinkoff.kora.resilient.timeout", "TimeoutExhaustedException");

    private final ProcessingEnvironment env;

    public TimeoutKoraAspect(ProcessingEnvironment env) {
        this.env = env;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(ANNOTATION_TYPE.canonicalName());
    }

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(ANNOTATION_TYPE);
    }

    @Override
    public ApplyResult apply(ExecutableElement method, String superCall, AspectContext aspectContext) {
        if (MethodUtils.isPublisher(method)) {
            throw new ProcessingErrorException("Publisher methods are not supported", method);
        }
        final Optional<? extends AnnotationMirror> mirror = method.getAnnotationMirrors().stream().filter(a -> a.getAnnotationType().toString().equals(ANNOTATION_TYPE.canonicalName())).findFirst();
        final String timeoutName = mirror.flatMap(a -> a.getElementValues().entrySet().stream()
                .filter(e -> e.getKey().getSimpleName().contentEquals("value"))
                .map(e -> String.valueOf(e.getValue().getValue())).findFirst())
            .orElseThrow();

        var managerType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement("ru.tinkoff.kora.resilient.timeout.TimeoutManager"));
        var fieldManager = aspectContext.fieldFactory().constructorParam(managerType, List.of());
        var metricsType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement("ru.tinkoff.kora.resilient.timeout.TimeoutMetrics"));
        var fieldMetrics = aspectContext.fieldFactory().constructorParam(metricsType, List.of(AnnotationSpec.builder(Nullable.class).build()));
        var timeouterType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement("ru.tinkoff.kora.resilient.timeout.Timeout"));
        var fieldTimeout = aspectContext.fieldFactory().constructorInitialized(timeouterType,
            CodeBlock.of("$L.get($S)", fieldManager, timeoutName));

        final CodeBlock body;
        if (MethodUtils.isFuture(method)) {
            body = buildBodyFuture(method, superCall, timeoutName, fieldTimeout, fieldMetrics);
        } else {
            body = buildBodySync(method, superCall, fieldTimeout);
        }

        return new ApplyResult.MethodBody(body);
    }

    private CodeBlock buildBodySync(ExecutableElement method, String superCall, String timeoutName) {
        final CodeBlock superMethod = buildMethodCall(method, superCall);

        if (MethodUtils.isVoid(method)) {
            return CodeBlock.builder().add("""
                $L.execute(() -> {
                    $L;
                    return null;
                });
                """, timeoutName, superMethod.toString()).build();
        } else {
            return CodeBlock.builder().add("""
                return $L.execute(() -> $L);
                """, timeoutName, superMethod.toString()).build();
        }
    }

    private CodeBlock buildBodyFuture(ExecutableElement method, String superCall, String timeoutName, String fieldTimeout, String fieldMetrics) {
        final CodeBlock superMethod = buildMethodCall(method, superCall);

        return CodeBlock.builder().add("""
                return $L.toCompletableFuture()
                    .orTimeout($L.timeout().toMillis(), $T.MILLISECONDS)
                    .exceptionallyCompose(_e -> {
                      var _cause = _e;
                      if (_cause instanceof $T ce) {
                          _cause = ce.getCause();
                      }
                      if(_cause instanceof $T) {
                        if($L != null) {
                            $L.recordTimeout($S, $L.timeout().toNanos());
                        }
                        return $T.failedFuture(new $T($S, "Timeout exceeded " + $L.timeout()));
                      } else {
                        return $T.failedFuture(_cause);
                      }
                    });""", superMethod.toString(), fieldTimeout, TimeUnit.class, CompletionException.class, TimeoutException.class,
            fieldMetrics, fieldMetrics, timeoutName, fieldTimeout, CompletableFuture.class,
            EXHAUSTED_EXCEPTION, timeoutName, fieldTimeout, CompletableFuture.class).build();
    }

    private CodeBlock buildBodyMono(ExecutableElement method, String superCall, String timeoutName, String fieldTimeout, String fieldMetrics) {
        final CodeBlock superMethod = buildMethodCall(method, superCall);

        return CodeBlock.builder().add("""
                return $L
                    .timeout($L.timeout())
                    .onErrorMap(e -> e instanceof $T, e -> new $T($S, "Timeout exceeded " + $L.timeout()))
                    .doOnError(e -> {
                        if(e instanceof $T && $L != null) {
                            $L.recordTimeout($S, $L.timeout().toNanos());
                        }
                    });
                """, superMethod.toString(), fieldTimeout, TimeoutException.class, EXHAUSTED_EXCEPTION, timeoutName,
            fieldTimeout, EXHAUSTED_EXCEPTION, fieldMetrics, fieldMetrics, timeoutName, fieldTimeout).build();
    }

    private CodeBlock buildBodyFlux(ExecutableElement method, String superCall, String timeoutName, String fieldTimeout, String fieldMetrics) {
        final CodeBlock superMethod = buildMethodCall(method, superCall);

        return CodeBlock.builder().add("""
                return $L
                    .timeout($L.timeout())
                    .onErrorMap(e -> e instanceof $T, e -> new $T($S, "Timeout exceeded " + $L.timeout()))
                    .doOnError(e -> {
                        if(e instanceof $T && $L != null) {
                            $L.recordTimeout($S, $L.timeout().toNanos());
                        }
                    });
                """, superMethod.toString(), fieldTimeout, TimeoutException.class, EXHAUSTED_EXCEPTION, timeoutName,
            fieldTimeout, EXHAUSTED_EXCEPTION, fieldMetrics, fieldMetrics, timeoutName, fieldTimeout).build();
    }

    private CodeBlock buildMethodCall(ExecutableElement method, String call) {
        return method.getParameters().stream().map(p -> CodeBlock.of("$L", p)).collect(joining(", ", call + "(", ")"));
    }
}
