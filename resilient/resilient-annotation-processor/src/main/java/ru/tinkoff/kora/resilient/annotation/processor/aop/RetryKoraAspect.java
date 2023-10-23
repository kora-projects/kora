package ru.tinkoff.kora.resilient.annotation.processor.aop;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
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
import java.util.function.Function;

import static com.squareup.javapoet.CodeBlock.joining;

public class RetryKoraAspect implements KoraAspect {

    private static final ClassName REACTOR_RETRY_BUILDER = ClassName.get("ru.tinkoff.kora.resilient.retry", "KoraRetryReactorBuilder");
    private static final ClassName REACTOR_RETRY = ClassName.get("reactor.util.retry", "Retry");
    private static final ClassName RETRY_EXCEPTION = ClassName.get("ru.tinkoff.kora.resilient.retry", "RetryExhaustedException");
    private static final String ANNOTATION_TYPE = "ru.tinkoff.kora.resilient.retry.annotation.Retry";
    private static final ClassName RETRY_STATE = ClassName.get("ru.tinkoff.kora.resilient.retry", "Retry", "RetryState", "RetryStatus");

    private final ProcessingEnvironment env;

    public RetryKoraAspect(ProcessingEnvironment env) {
        this.env = env;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(ANNOTATION_TYPE);
    }

    @Override
    public ApplyResult apply(ExecutableElement method, String superCall, AspectContext aspectContext) {
        final Optional<? extends AnnotationMirror> mirror = method.getAnnotationMirrors().stream().filter(a -> a.getAnnotationType().toString().equals(ANNOTATION_TYPE)).findFirst();
        final String retryableName = mirror.flatMap(a -> a.getElementValues().entrySet().stream()
                .filter(e -> e.getKey().getSimpleName().contentEquals("value"))
                .map(e -> String.valueOf(e.getValue().getValue())).findFirst())
            .orElseThrow();

        final CodeBlock body;
        if (MethodUtils.isMono(method)) {
            var reactorBuilderType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement(REACTOR_RETRY_BUILDER.canonicalName()));
            var reactorBuilderField = aspectContext.fieldFactory().constructorParam(reactorBuilderType, List.of());
            var retrierType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement(REACTOR_RETRY.canonicalName()));
            var fieldRetrier = aspectContext.fieldFactory().constructorInitialized(retrierType, CodeBlock.of("$L.get($S);", reactorBuilderField, retryableName));
            body = buildBodyMono(method, superCall, fieldRetrier);
        } else if (MethodUtils.isFlux(method)) {
            var reactorBuilderType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement(REACTOR_RETRY_BUILDER.canonicalName()));
            var reactorBuilderField = aspectContext.fieldFactory().constructorParam(reactorBuilderType, List.of());
            var retrierType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement(REACTOR_RETRY.canonicalName()));
            var fieldRetrier = aspectContext.fieldFactory().constructorInitialized(retrierType, CodeBlock.of("$L.get($S);", reactorBuilderField, retryableName));
            body = buildBodyFlux(method, superCall, fieldRetrier);
        } else if (MethodUtils.isFuture(method)) {
            var managerType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement("ru.tinkoff.kora.resilient.retry.RetryManager"));
            var fieldManager = aspectContext.fieldFactory().constructorParam(managerType, List.of());
            var retrierType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement("ru.tinkoff.kora.resilient.retry.Retry"));
            var fieldRetrier = aspectContext.fieldFactory().constructorInitialized(retrierType,
                CodeBlock.of("$L.get($S);", fieldManager, retryableName));
            body = buildBodyFuture(method, superCall, fieldRetrier);
        } else {
            var managerType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement("ru.tinkoff.kora.resilient.retry.RetryManager"));
            var fieldManager = aspectContext.fieldFactory().constructorParam(managerType, List.of());
            var retrierType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement("ru.tinkoff.kora.resilient.retry.Retry"));
            var fieldRetrier = aspectContext.fieldFactory().constructorInitialized(retrierType, CodeBlock.of("$L.get($S);", fieldManager, retryableName));
            body = buildBodySync(method, superCall, fieldRetrier);
        }

        return new ApplyResult.MethodBody(body);
    }

    private CodeBlock buildBodySync(ExecutableElement method, String superCall, String fieldRetry) {
        var builder = CodeBlock.builder();

        if (MethodUtils.isVoid(method)) {
            builder.addStatement("$L.retry(() -> $L)", fieldRetry, buildMethodCall(method, superCall));
        } else {
            builder.addStatement("return $L.retry(() -> $L)", fieldRetry, buildMethodCall(method, superCall));
        }

        return builder.build();
    }

    private CodeBlock buildBodyFuture(ExecutableElement method, String superCall, String fieldRetry) {
        var builder = CodeBlock.builder();

        var methodCall = buildMethodCall(method, superCall);
        builder.add("""
                var _future = $L;
                var _retryState = $L.asState();
                for (int _i = 0; _i <= _retryState.getAttemptsMax(); _i++) {
                    _future = _future.thenApply($T::completedFuture)
                            .exceptionally(_e -> {
                                var _ex = (_e instanceof $T)
                                        ? _e.getCause()
                                        : _e;
                                        
                                var _state = _retryState.onException(_ex);
                                if(_state == $T.ACCEPTED) {
                                    return $T.runAsync(_retryState::doDelay).thenCompose(_r -> $L);
                                } else if(_state == $T.REJECTED) {
                                    _retryState.close();
                                    return $T.failedFuture(_ex);
                                } else {
                                    _retryState.close();
                                    return $T.failedFuture(new $T(_retryState.getAttemptsMax(), _ex));
                                }
                            })
                            .thenCompose($T.identity());
                }
                return _future;
                """, methodCall, fieldRetry, CompletableFuture.class, CompletionException.class, RETRY_STATE, CompletableFuture.class,
            methodCall, RETRY_STATE, CompletableFuture.class, CompletableFuture.class, RETRY_EXCEPTION, Function.class);

        return builder.build();
    }

    private CodeBlock buildBodyMono(ExecutableElement method, String superCall, String fieldRetry) {
        return CodeBlock.builder().add("""
            return $L.retryWhen($L);
            """, buildMethodCall(method, superCall), fieldRetry).build();
    }

    private CodeBlock buildBodyFlux(ExecutableElement method, String superCall, String fieldRetry) {
        return CodeBlock.builder().add("""
            return $L.retryWhen($L);
            """, buildMethodCall(method, superCall), fieldRetry).build();
    }

    private CodeBlock buildMethodCall(ExecutableElement method, String call) {
        return method.getParameters().stream().map(p -> CodeBlock.of("$L", p)).collect(joining(", ", call + "(", ")"));
    }
}
