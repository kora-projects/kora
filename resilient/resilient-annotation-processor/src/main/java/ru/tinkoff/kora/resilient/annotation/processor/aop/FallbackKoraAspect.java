package ru.tinkoff.kora.resilient.annotation.processor.aop;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import ru.tinkoff.kora.annotation.processor.common.MethodUtils;
import ru.tinkoff.kora.aop.annotation.processor.KoraAspect;

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

    private static final ClassName ANNOTATION_TYPE = ClassName.get("ru.tinkoff.kora.resilient.fallback.annotation", "Fallback");

    private final ProcessingEnvironment env;

    public FallbackKoraAspect(ProcessingEnvironment env) {
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
        final Optional<? extends AnnotationMirror> mirror = method.getAnnotationMirrors().stream().filter(a -> a.getAnnotationType().toString().equals(ANNOTATION_TYPE.canonicalName())).findFirst();
        final FallbackMeta fallback = mirror.flatMap(a -> a.getElementValues().entrySet().stream()
                .filter(e -> e.getKey().getSimpleName().contentEquals("method"))
                .map(e -> String.valueOf(e.getValue().getValue())).findFirst()
                .filter(v -> !v.isBlank()))
            .map(v -> FallbackMeta.ofFallbackMethod(v, method))
            .orElseThrow(() -> new IllegalStateException("Method argument for @Fallback is mandatory!"));
        final String name = mirror.flatMap(a -> a.getElementValues().entrySet().stream()
                .filter(e -> e.getKey().getSimpleName().contentEquals("value"))
                .map(e -> String.valueOf(e.getValue().getValue())).findFirst())
            .orElseThrow();

        var managerType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement("ru.tinkoff.kora.resilient.fallback.FallbackManager"));
        var fieldManager = aspectContext.fieldFactory().constructorParam(managerType, List.of());
        var fallbackType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement("ru.tinkoff.kora.resilient.fallback.Fallback"));
        var fieldFallback = aspectContext.fieldFactory().constructorInitialized(
            fallbackType, CodeBlock.of("$L.get($S)", fieldManager, name));

        final CodeBlock body;
        if (MethodUtils.isMono(method)) {
            body = buildBodyMono(method, fallback, superCall, fieldFallback);
        } else if (MethodUtils.isFlux(method)) {
            body = buildBodyFlux(method, fallback, superCall, fieldFallback);
        } else if (MethodUtils.isFuture(method)) {
            body = buildBodyFuture(method, fallback, superCall, fieldFallback);
        } else {
            body = buildBodySync(method, fallback, superCall, fieldFallback);
        }

        return new ApplyResult.MethodBody(body);
    }

    private CodeBlock buildBodySync(ExecutableElement method, FallbackMeta fallbackCall, String superCall, String fieldFallback) {
        final String fallbackMethod = fallbackCall.call();
        if (MethodUtils.isVoid(method)) {
            final CodeBlock superMethod = buildMethodCall(method, superCall);
            return CodeBlock.builder().add("""
                try {
                    $L;
                } catch (Throwable _e) {
                    if ($L.canFallback(_e)) {
                        $L;
                    } else {
                        throw _e;
                    }
                }
                """, superMethod.toString(), fieldFallback, fallbackMethod).build();
        } else {
            final CodeBlock superMethod = buildMethodCall(method, superCall);
            return CodeBlock.builder().add("""
                try {
                    return $L;
                } catch (Throwable _e) {
                    if ($L.canFallback(_e)) {
                        return $L;
                    } else {
                        throw _e;
                    }
                }
                """, superMethod.toString(), fieldFallback, fallbackMethod).build();
        }
    }

    private CodeBlock buildBodyFuture(ExecutableElement method, FallbackMeta fallbackCall, String superCall, String fieldFallback) {
        final String fallbackMethod = fallbackCall.call();
        final CodeBlock superMethod = buildMethodCall(method, superCall);

        return CodeBlock.builder().add("""
                return $L.exceptionallyCompose(_e -> {
                    var _cause = _e;
                    if (_cause instanceof $T ce) {
                        _cause = ce.getCause();
                    }
                    if ($L.canFallback(_cause)) {
                        return $L;
                    }
                    return $T.failedFuture(_cause);
                });""", superMethod.toString(), CompletionException.class, fieldFallback, fallbackMethod,
            CompletableFuture.class).build();
    }

    private CodeBlock buildBodyMono(ExecutableElement method, FallbackMeta fallbackCall, String superCall, String fieldFallback) {
        final CodeBlock superMethod = buildMethodCall(method, superCall);
        final String fallbackMethod = fallbackCall.call();
        return CodeBlock.builder().add("""
            return $L.onErrorResume($L::canFallback, _e -> $L);
            """, superMethod.toString(), fieldFallback, fallbackMethod).build();
    }

    private CodeBlock buildBodyFlux(ExecutableElement method, FallbackMeta fallbackCall, String superCall, String fieldFallback) {
        final CodeBlock superMethod = buildMethodCall(method, superCall);
        final String fallbackMethod = fallbackCall.call();
        return CodeBlock.builder().add("""
            return $L.onErrorResume($L::canFallback, _e -> $L);
            """, superMethod.toString(), fieldFallback, fallbackMethod).build();
    }

    private CodeBlock buildMethodCall(ExecutableElement method, String call) {
        return method.getParameters().stream().map(p -> CodeBlock.of("$L", p)).collect(joining(", ", call + "(", ")"));
    }
}
