package ru.tinkoff.kora.resilient.annotation.processor.aop;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import ru.tinkoff.kora.annotation.processor.common.MethodUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.aop.annotation.processor.KoraAspect;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;

import static com.squareup.javapoet.CodeBlock.joining;

public class RetryableKoraAspect implements KoraAspect {

    private static final ClassName RETRY_STATUS = ClassName.get("ru.tinkoff.kora.resilient.retry", "Retrier", "RetryState", "RetryStatus");
    private static final ClassName RETRY_EXCEPTION = ClassName.get("ru.tinkoff.kora.resilient.retry", "RetryAttemptException");
    private static final String ANNOTATION_TYPE = "ru.tinkoff.kora.resilient.retry.annotation.Retryable";

    private final ProcessingEnvironment env;

    public RetryableKoraAspect(ProcessingEnvironment env) {
        this.env = env;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(ANNOTATION_TYPE);
    }

    @Override
    public ApplyResult apply(ExecutableElement method, String superCall, AspectContext aspectContext) {
        if (MethodUtils.isFuture(method, env)) {
            throw new ProcessingErrorException("@Retryable can't be applied for types assignable from " + Future.class, method);
        }

        final Optional<? extends AnnotationMirror> mirror = method.getAnnotationMirrors().stream().filter(a -> a.getAnnotationType().toString().equals(ANNOTATION_TYPE)).findFirst();
        final String retryableName = mirror.flatMap(a -> a.getElementValues().entrySet().stream()
                .filter(e -> e.getKey().getSimpleName().contentEquals("value"))
                .map(e -> String.valueOf(e.getValue().getValue())).findFirst())
            .orElseThrow();

        var managerType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement("ru.tinkoff.kora.resilient.retry.RetrierManager"));
        var fieldManager = aspectContext.fieldFactory().constructorParam(managerType, List.of());
        var retrierType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement("ru.tinkoff.kora.resilient.retry.Retrier"));
        var fieldRetrier = aspectContext.fieldFactory().constructorInitialized(retrierType,
            CodeBlock.of("$L.get($S);", fieldManager, retryableName));

        final CodeBlock body;
        if (MethodUtils.isMono(method, env)) {
            body = buildBodyMono(method, superCall, fieldRetrier);
        } else if (MethodUtils.isFlux(method, env)) {
            body = buildBodyFlux(method, superCall, fieldRetrier);
        } else {
            body = buildBodySync(method, superCall, fieldRetrier);
        }

        return new ApplyResult.MethodBody(body);
    }

    private CodeBlock buildBodySync(ExecutableElement method, String superCall, String fieldRetry) {
        var builder = CodeBlock.builder()
            .addStatement("var _cause = (Exception) null")
            .beginControlFlow("try (var _state = $L.asState())", fieldRetry)
            .beginControlFlow("while (true)")
            .beginControlFlow("try");

        if (MethodUtils.isVoid(method)) {
            builder.addStatement(buildMethodCall(method, superCall));
            builder.addStatement("return");
        } else {
            builder.add("return ").addStatement(buildMethodCall(method, superCall));
        }

        builder.nextControlFlow("catch (Exception _e)");
        builder.addStatement("var _status = _state.onException(_e)");
        builder.beginControlFlow("if ($T.REJECTED == _status)", RETRY_STATUS)
            .addStatement("throw _e")
            .nextControlFlow("else if($T.ACCEPTED == _status)", RETRY_STATUS)
            .add("""
                if (_cause == null) {
                    _cause = _e;
                } else {
                    _cause.addSuppressed(_e);
                }
                _state.doDelay();
                """)
            .nextControlFlow("else if($T.EXHAUSTED  == _status)", RETRY_STATUS)
            .add("""
                var _exhaustedException = new $T(_state.getAttempts());
                if (_cause != null) {
                    _exhaustedException.addSuppressed(_cause);
                }
                throw _exhaustedException;
                """, RETRY_EXCEPTION)
            .endControlFlow();

        builder
            .endControlFlow()   // try
            .endControlFlow()   // while
            .endControlFlow();  // try retry state

        return builder.build();
    }

    private CodeBlock buildBodyMono(ExecutableElement method, String superCall, String fieldRetry) {
        return CodeBlock.builder().add("""
            return $L.retryWhen($L.asReactor());
            """, buildMethodCall(method, superCall), fieldRetry).build();
    }

    private CodeBlock buildBodyFlux(ExecutableElement method, String superCall, String fieldRetry) {
        return CodeBlock.builder().add("""
            return $L.retryWhen($L.asReactor());
            """, buildMethodCall(method, superCall), fieldRetry).build();
    }

    private CodeBlock buildMethodCall(ExecutableElement method, String call) {
        return method.getParameters().stream().map(p -> CodeBlock.of("$L", p)).collect(joining(", ", call + "(", ")"));
    }

    private CodeBlock buildMethodCallable(ExecutableElement method, String call) {
        return method.getParameters().stream().map(p -> CodeBlock.of("$L", p)).collect(joining(", ", "() -> " + call + "(", ")"));
    }
}
