package ru.tinkoff.kora.resilient.annotation.processor.aop;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import ru.tinkoff.kora.annotation.processor.common.MethodUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingError;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.aop.annotation.processor.KoraAspect;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
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
        if (MethodUtils.isFuture(method)) {
            throw new ProcessingErrorException("@CircuitBreaker can't be applied for types assignable from " + Future.class, method);
        }

        final Optional<? extends AnnotationMirror> mirror = method.getAnnotationMirrors().stream().filter(a -> a.getAnnotationType().toString().equals(ANNOTATION_TYPE)).findFirst();
        final String circuitBreakerName = mirror.flatMap(a -> a.getElementValues().entrySet().stream()
                .filter(e -> e.getKey().getSimpleName().contentEquals("value"))
                .map(e -> String.valueOf(e.getValue().getValue())).findFirst())
            .orElseThrow();

        var managerType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement("ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreakerManager"));
        var fieldManager = aspectContext.fieldFactory().constructorParam(managerType, List.of());
        var circuitType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement("ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreaker"));
        var fieldCircuit = aspectContext.fieldFactory().constructorInitialized(circuitType,
            CodeBlock.of("$L.get($S);", fieldManager, circuitBreakerName));

        final CodeBlock body;
        if (MethodUtils.isMono(method)) {
            body = buildBodyMono(method, superCall, fieldCircuit);
        } else if (MethodUtils.isFlux(method)) {
            body = buildBodyFlux(method, superCall, fieldCircuit);
        } else {
            body = buildBodySync(method, superCall, fieldCircuit);
        }

        return new ApplyResult.MethodBody(body);
    }

    private CodeBlock buildBodySync(ExecutableElement method, String superCall, String circuitBreakerField) {
        final CodeBlock superMethod = buildMethodCall(method, superCall);
        final CodeBlock methodCall = MethodUtils.isVoid(method)
            ? superMethod
            : CodeBlock.of("var t = $L", superMethod.toString());

        final CodeBlock returnCall = MethodUtils.isVoid(method)
            ? CodeBlock.of("return")
            : CodeBlock.of("return t", superMethod.toString());

        final ClassName cbException = ClassName.get("ru.tinkoff.kora.resilient.timeout", "TimeoutExhaustedException");

        return CodeBlock.builder().add("""
            var _circuitBreaker = $L;
            try {
                _circuitBreaker.acquire();
                $L;
                _circuitBreaker.releaseOnSuccess();
                $L;
            } catch ($T e) {
                throw e;
            } catch (Exception e) {
                _circuitBreaker.releaseOnError(e);
                throw e;
            }
            """, circuitBreakerField, methodCall.toString(), returnCall.toString(), cbException).build();
    }

    private CodeBlock buildBodyMono(ExecutableElement method, String superCall, String circuitBreakerField) {
        final CodeBlock superMethod = buildMethodCall(method, superCall);

        return CodeBlock.builder().add("""
            var _circuitBreaker = $L;
                        
            return Mono.defer(() -> {
                  _circuitBreaker.acquire();
                  return $L
                      .doOnSuccess(r -> _circuitBreaker.releaseOnSuccess())
                      .doOnError(_circuitBreaker::releaseOnError);
            });
                 """, circuitBreakerField, superMethod.toString()).build();
    }

    private CodeBlock buildBodyFlux(ExecutableElement method, String superCall, String circuitBreakerField) {
        final CodeBlock superMethod = buildMethodCall(method, superCall);

        return CodeBlock.builder().add("""
            var _circuitBreaker = $L;
                        
            return Flux.defer(() -> {
                  _circuitBreaker.acquire();
                  return $L
                      .doOnComplete(() -> _circuitBreaker.releaseOnSuccess())
                      .doOnError(_circuitBreaker::releaseOnError);
            });
                 """, circuitBreakerField, superMethod.toString()).build();
    }

    private CodeBlock buildMethodCall(ExecutableElement method, String call) {
        return method.getParameters().stream().map(p -> CodeBlock.of("$L", p)).collect(joining(", ", call + "(", ")"));
    }
}
