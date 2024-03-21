package ru.tinkoff.kora.resilient.annotation.processor.aop;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
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

import static com.squareup.javapoet.CodeBlock.joining;

public class CircuitBreakerKoraAspect implements KoraAspect {

    private static final String ANNOTATION_TYPE = "ru.tinkoff.kora.resilient.circuitbreaker.annotation.CircuitBreaker";
    private static final ClassName PERMITTED_EXCEPTION = ClassName.get("ru.tinkoff.kora.resilient.circuitbreaker", "CallNotPermittedException");

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
        final Optional<? extends AnnotationMirror> mirror = method.getAnnotationMirrors().stream().filter(a -> a.getAnnotationType().toString().equals(ANNOTATION_TYPE)).findFirst();
        final String circuitBreakerName = mirror.flatMap(a -> a.getElementValues().entrySet().stream()
                .filter(e -> e.getKey().getSimpleName().contentEquals("value"))
                .map(e -> String.valueOf(e.getValue().getValue())).findFirst())
            .orElseThrow();

        var managerType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement("ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreakerManager"));
        var fieldManager = aspectContext.fieldFactory().constructorParam(managerType, List.of());
        var circuitType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement("ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreaker"));
        var fieldCircuit = aspectContext.fieldFactory().constructorInitialized(circuitType,
            CodeBlock.of("$L.get($S)", fieldManager, circuitBreakerName));

        final CodeBlock body;
        if (MethodUtils.isMono(method)) {
            body = buildBodyMono(method, superCall, fieldCircuit);
        } else if (MethodUtils.isFlux(method)) {
            body = buildBodyFlux(method, superCall, fieldCircuit);
        } else if (MethodUtils.isFuture(method)) {
            body = buildBodyFuture(method, superCall, fieldCircuit);
        } else {
            body = buildBodySync(method, superCall, fieldCircuit);
        }

        return new ApplyResult.MethodBody(body);
    }

    private CodeBlock buildBodySync(ExecutableElement method, String superCall, String cbField) {
        final CodeBlock superMethod = buildMethodCall(method, superCall);
        final CodeBlock methodCall = MethodUtils.isVoid(method)
            ? superMethod
            : CodeBlock.of("var t = $L", superMethod.toString());

        final CodeBlock returnCall = MethodUtils.isVoid(method)
            ? CodeBlock.of("return")
            : CodeBlock.of("return t", superMethod.toString());

        return CodeBlock.builder().add("""
            try {
                $L.acquire();
                $L;
                $L.releaseOnSuccess();
                $L;
            } catch ($T e) {
                throw e;
            } catch (Exception e) {
                $L.releaseOnError(e);
                throw e;
            }
            """, cbField, methodCall.toString(), cbField, returnCall.toString(), PERMITTED_EXCEPTION, cbField).build();
    }

    private CodeBlock buildBodyFuture(ExecutableElement method, String superCall, String cbField) {
        final CodeBlock superMethod = buildMethodCall(method, superCall);

        return CodeBlock.builder().add("""
                try {
                    $L.acquire();
                    return $L.thenApply(_r -> {
                                $L.releaseOnSuccess();
                                return _r;
                            })
                            .exceptionally(e -> {
                                $L.releaseOnError(e);
                                if(e instanceof $T ex) {
                                    throw ex;
                                }
                                throw new $T(e);
                            });
                } catch ($T e) {
                    return $T.failedFuture(e);
                }
                """, cbField, superMethod, cbField, cbField, RuntimeException.class,
            CompletionException.class, PERMITTED_EXCEPTION, CompletableFuture.class).build();
    }

    private CodeBlock buildBodyMono(ExecutableElement method, String superCall, String cbField) {
        final CodeBlock superMethod = buildMethodCall(method, superCall);

        return CodeBlock.builder().add("""
            return $T.defer(() -> {
                  $L.acquire();
                  return $L
                      .doOnSuccess(r -> $L.releaseOnSuccess())
                      .doOnCancel($L::releaseOnSuccess)
                      .doOnError($L::releaseOnError);
            });
            """, CommonClassNames.mono, cbField, superMethod.toString(), cbField, cbField, cbField).build();
    }

    private CodeBlock buildBodyFlux(ExecutableElement method, String superCall, String cbField) {
        final CodeBlock superMethod = buildMethodCall(method, superCall);

        return CodeBlock.builder().add("""
            return $T.defer(() -> {
                  $L.acquire();
                  return $L
                      .doOnComplete($L::releaseOnSuccess)
                      .doOnCancel($L::releaseOnSuccess)
                      .doOnError($L::releaseOnError);
            });
            """, CommonClassNames.flux, cbField, superMethod.toString(), cbField, cbField, cbField).build();
    }

    private CodeBlock buildMethodCall(ExecutableElement method, String call) {
        return method.getParameters().stream().map(p -> CodeBlock.of("$L", p)).collect(joining(", ", call + "(", ")"));
    }
}
