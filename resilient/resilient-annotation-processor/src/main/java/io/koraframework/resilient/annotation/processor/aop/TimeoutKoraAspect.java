package io.koraframework.resilient.annotation.processor.aop;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.annotation.processor.common.MethodUtils;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.aop.annotation.processor.KoraAspect;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.palantir.javapoet.CodeBlock.joining;

public class TimeoutKoraAspect implements KoraAspect {

    private static final ClassName ANNOTATION_TYPE = ClassName.get("io.koraframework.resilient.timeout.annotation", "Timeout");
    private static final ClassName TIMEOUT = ClassName.get("io.koraframework.resilient.timeout", "Timeouter");
    private static final ClassName EXHAUSTED_EXCEPTION = ClassName.get("io.koraframework.resilient.timeout.exception", "TimeoutExhaustedException");

    private final ProcessingEnvironment env;

    public TimeoutKoraAspect(ProcessingEnvironment env) {
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
        final TypeMirror timeoutTypeMirror = mirror.flatMap(a -> a.getElementValues().entrySet().stream()
                .filter(e -> e.getKey().getSimpleName().contentEquals("value"))
                .map(e -> (TypeMirror) e.getValue().getValue())
                .findFirst())
            .orElseThrow();

        var timeoutElement = (TypeElement) env.getTypeUtils().asElement(timeoutTypeMirror);
        var baseTimeoutType = env.getElementUtils().getTypeElement(TIMEOUT.canonicalName()).asType();
        if (!env.getTypeUtils().isAssignable(timeoutTypeMirror, baseTimeoutType)) {
            throw new ProcessingErrorException("@%s value must extend %s".formatted(ANNOTATION_TYPE.simpleName(), TIMEOUT.canonicalName()), method);
        }
        var timeoutType = env.getTypeUtils().getDeclaredType(timeoutElement);
        var fieldTimeout = aspectContext.fieldFactory().constructorParam(timeoutType, List.of());

        final CodeBlock body;
        if (MethodUtils.isCompletionStage(method)) {
            body = buildBodyCompletableStage(method, superCall, timeoutElement.getSimpleName().toString(), fieldTimeout);
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

    private CodeBlock buildBodyCompletableStage(ExecutableElement method, String superCall, String timeoutName, String fieldTimeout) {
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
                        return $T.failedFuture(new $T($S, "Timeout exceeded " + $L.timeout()));
                      } else {
                        return $T.failedFuture(_cause);
                      }
                    });""", superMethod.toString(), fieldTimeout, TimeUnit.class,
            CompletionException.class, TimeoutException.class, CompletableFuture.class,
            EXHAUSTED_EXCEPTION, timeoutName, fieldTimeout, CompletableFuture.class).build();
    }

    private CodeBlock buildMethodCall(ExecutableElement method, String call) {
        return method.getParameters().stream().map(p -> CodeBlock.of("$L", p)).collect(joining(", ", call + "(", ")"));
    }
}
