package io.koraframework.resilient.annotation.processor.aop;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.annotation.processor.common.MethodUtils;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.aop.annotation.processor.KoraAspect;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static com.palantir.javapoet.CodeBlock.joining;

public class CircuitBreakerKoraAspect implements KoraAspect {

    private static final ClassName ANNOTATION_TYPE = ClassName.get("io.koraframework.resilient.circuitbreaker.annotation", "CircuitBreakable");
    private static final ClassName CIRCUIT_BREAKER = ClassName.get("io.koraframework.resilient.circuitbreaker", "CircuitBreaker");
    private static final ClassName PERMITTED_EXCEPTION = ClassName.get("io.koraframework.resilient.circuitbreaker.exception", "CallNotPermittedException");

    private final ProcessingEnvironment env;

    public CircuitBreakerKoraAspect(ProcessingEnvironment env) {
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
        } else if(MethodUtils.isFuture(method)) {
            throw new ProcessingErrorException("@%s can't be applied for type ".formatted(ANNOTATION_TYPE) + method.getReturnType().toString(), method);
        }

        final Optional<? extends AnnotationMirror> mirror = method.getAnnotationMirrors().stream()
            .filter(a -> a.getAnnotationType().toString().equals(ANNOTATION_TYPE.canonicalName()))
            .findFirst();
        final TypeMirror circuitBreakerTypeMirror = mirror.flatMap(a -> a.getElementValues().entrySet().stream()
                .filter(e -> e.getKey().getSimpleName().contentEquals("value"))
                .map(e -> (TypeMirror) e.getValue().getValue())
                .findFirst())
            .orElseThrow();

        var circuitBreakerElement = (TypeElement) env.getTypeUtils().asElement(circuitBreakerTypeMirror);
        var baseCircuitBreakerType = env.getElementUtils().getTypeElement(CIRCUIT_BREAKER.canonicalName()).asType();
        if (!env.getTypeUtils().isAssignable(circuitBreakerTypeMirror, baseCircuitBreakerType)) {
            throw new ProcessingErrorException("@%s value must extend %s".formatted(ANNOTATION_TYPE.simpleName(), CIRCUIT_BREAKER.canonicalName()), method);
        }

        var circuitType = env.getTypeUtils().getDeclaredType(circuitBreakerElement);
        final String fieldCircuit = aspectContext.fieldFactory().constructorParam(circuitType, java.util.List.of());

        final CodeBlock body;
        if (MethodUtils.isCompletionStage(method)) {
            body = buildBodyCompletableStage(method, superCall, fieldCircuit);
        } else {
            body = buildBodySync(method, superCall, fieldCircuit);
        }

        return new ApplyResult.MethodBody(body);
    }

    private CodeBlock buildBodySync(ExecutableElement method, String superCall, String cbField) {
        final CodeBlock superMethod = buildMethodCall(method, superCall);
        final CodeBlock methodCall = MethodUtils.isVoid(method)
            ? superMethod
            : CodeBlock.of("var _result = $L", superMethod.toString());

        final CodeBlock returnCall = MethodUtils.isVoid(method)
            ? CodeBlock.of("return")
            : CodeBlock.of("return _result");

        return CodeBlock.builder().add("""
            try {
                $L.acquire();
                $L;
                $L.releaseOnSuccess();
                $L;
            } catch ($T _e) {
                throw _e;
            } catch (Throwable _e) {
                $L.releaseOnError(_e);
                throw _e;
            }
            """, cbField, methodCall.toString(), cbField, returnCall.toString(), PERMITTED_EXCEPTION, cbField).build();
    }

    private CodeBlock buildBodyCompletableStage(ExecutableElement method, String superCall, String cbField) {
        final CodeBlock superMethod = buildMethodCall(method, superCall);

        return CodeBlock.builder().add("""
            try {
                $L.acquire();
                return $L.whenComplete((_r, _e) -> {
                    if (_e != null) {
                        if (_e instanceof $T ce) {
                            _e = ce.getCause();
                        }
                        $L.releaseOnError(_e);
                    } else {
                        $L.releaseOnSuccess();
                    }
                });
            } catch ($T _e) {
                return $T.failedFuture(_e);
            } catch (Throwable _e) {
                $L.releaseOnError(_e);
                throw _e;
            }
            """, cbField, superMethod, CompletionException.class, cbField, cbField, PERMITTED_EXCEPTION, CompletableFuture.class, cbField).build();
    }

    private CodeBlock buildMethodCall(ExecutableElement method, String call) {
        return method.getParameters().stream().map(p -> CodeBlock.of("$L", p)).collect(joining(", ", call + "(", ")"));
    }
}
