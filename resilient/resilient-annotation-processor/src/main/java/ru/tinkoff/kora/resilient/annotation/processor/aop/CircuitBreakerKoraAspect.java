package ru.tinkoff.kora.resilient.annotation.processor.aop;

import com.squareup.javapoet.CodeBlock;
import ru.tinkoff.kora.annotation.processor.common.MethodUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingError;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.aop.annotation.processor.KoraAspect;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;

import static com.squareup.javapoet.CodeBlock.joining;

public class CircuitBreakerKoraAspect implements KoraAspect {

    private static final String ANNOTATION_TYPE = "ru.tinkoff.kora.resilient.circuitbreaker.annotation.CircuitBreaker";

    private final ProcessingEnvironment env;

    public CircuitBreakerKoraAspect(ProcessingEnvironment env) {
        this.env = env;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(ANNOTATION_TYPE);
    }

    @Override
    public ApplyResult apply(ExecutableElement method, String superCall, AspectContext aspectContext) {
        if (MethodUtils.isFuture(method, env)) {
            throw new ProcessingErrorException(new ProcessingError(Diagnostic.Kind.NOTE, "@CircuitBreaker can't be applied for types assignable from " + Future.class, method));
        }
        if (MethodUtils.isVoid(method)) {
            throw new ProcessingErrorException(new ProcessingError(Diagnostic.Kind.NOTE, "@CircuitBreaker can't be applied for types assignable from " + Void.class, method));
        }

        final Optional<? extends AnnotationMirror> mirror = method.getAnnotationMirrors().stream().filter(a -> a.getAnnotationType().toString().equals(ANNOTATION_TYPE)).findFirst();
        final String fallbackMethod = mirror.flatMap(a -> a.getElementValues().entrySet().stream()
                .filter(e -> e.getKey().getSimpleName().contentEquals("fallbackMethod"))
                .map(e -> String.valueOf(e.getValue().getValue())).findFirst()
                .filter(v -> !v.isBlank()))
            .orElse("");
        final String circuitBreakerName = mirror.flatMap(a -> a.getElementValues().entrySet().stream()
                .filter(e -> e.getKey().getSimpleName().contentEquals("value"))
                .map(e -> String.valueOf(e.getValue().getValue())).findFirst())
            .orElseThrow();

        var managerType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement("ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreakerManager"));
        var fieldManager = aspectContext.fieldFactory().constructorParam(managerType, List.of());

        final CodeBlock body;
        if (MethodUtils.isMono(method, env)) {
            body = buildBodyMono(method, fallbackMethod, superCall, circuitBreakerName, fieldManager);
        } else if (MethodUtils.isFlux(method, env)) {
            body = buildBodyFlux(method, fallbackMethod, superCall, circuitBreakerName, fieldManager);
        } else {
            body = buildBodySync(method, fallbackMethod, superCall, circuitBreakerName, fieldManager);
        }

        return new ApplyResult.MethodBody(body);
    }

    private CodeBlock buildBodySync(ExecutableElement method, String fallbackCall, String superCall, String circuitBreakerName, String fieldManager) {
        final CodeBlock superMethod = buildMethodCall(method, superCall);
        final CodeBlock fallbackMethod = (fallbackCall.isEmpty())
            ? CodeBlock.of("throw e;")
            : CodeBlock.of("return $L;", buildMethodCall(method, fallbackCall));
        final String returnType = method.getReturnType().toString();

        return CodeBlock.builder().add("""
            var circuitBreaker = $L.get("$L");
            try {
                circuitBreaker.acquire();
                final $L t = $L;
                circuitBreaker.releaseOnSuccess();
                return t;
            } catch (ru.tinkoff.kora.resilient.circuitbreaker.CallNotPermittedException e) {
                $L
            } catch (Exception e) {
                circuitBreaker.releaseOnError(e);
                throw e;
            }
            """, fieldManager, circuitBreakerName, returnType, superMethod.toString(), fallbackMethod.toString()).build();
    }

    private CodeBlock buildBodyMono(ExecutableElement method, String fallbackCall, String superCall, String circuitBreakerName, String fieldManager) {
        final CodeBlock superMethod = buildMethodCall(method, superCall);
        final CodeBlock fallbackMethod = (fallbackCall.isEmpty())
            ? CodeBlock.of("return Mono.error(e);")
            : CodeBlock.of("return $L;", buildMethodCall(method, fallbackCall).toString());
        final TypeMirror erasure = MethodUtils.getGenericType(method.getReturnType()).orElseThrow();

        return CodeBlock.builder().add("""
            var superCall = $L;
            var circuitBreaker = $L.get("$L");
            return Mono.fromRunnable(circuitBreaker::acquire)
                     .switchIfEmpty(superCall.doOnSuccess((r) -> circuitBreaker.releaseOnSuccess()))
                     .cast($L.class)
                     .onErrorResume(e -> {
                         if (e instanceof ru.tinkoff.kora.resilient.circuitbreaker.CallNotPermittedException) {
                            $L
                         }
                             
                         circuitBreaker.releaseOnError(e);
                         return Mono.error(e);
                     });
                 """, superMethod.toString(), fieldManager, circuitBreakerName, erasure.toString(), fallbackMethod.toString()).build();
    }

    private CodeBlock buildBodyFlux(ExecutableElement method, String fallbackCall, String superCall, String circuitBreakerName, String fieldManager) {
        final CodeBlock superMethod = buildMethodCall(method, superCall);
        final CodeBlock fallbackMethod = (fallbackCall.isEmpty())
            ? CodeBlock.of("return Flux.error(e);")
            : CodeBlock.of("return $L;", buildMethodCall(method, fallbackCall).toString());
        final TypeMirror erasure = MethodUtils.getGenericType(method.getReturnType()).orElseThrow();

        return CodeBlock.builder().add("""
            var superCall = $L;
            var circuitBreaker = $L.get("$L");

            return Flux.from(Mono.fromRunnable(circuitBreaker::acquire))
                .switchIfEmpty(superCall.doOnComplete(circuitBreaker::releaseOnSuccess))
                .cast($L.class)
                .onErrorResume(e -> {
                    if (e instanceof ru.tinkoff.kora.resilient.circuitbreaker.CallNotPermittedException) {
                        $L
                    }
                        
                    circuitBreaker.releaseOnError(e);
                    return Flux.error(e);
                });
            """, superMethod.toString(), fieldManager, circuitBreakerName, erasure.toString(), fallbackMethod.toString()).build();
    }

    private CodeBlock buildMethodCall(ExecutableElement method, String call) {
        return method.getParameters().stream().map(p -> CodeBlock.of("$L", p)).collect(joining(", ", call + "(", ")"));
    }
}
