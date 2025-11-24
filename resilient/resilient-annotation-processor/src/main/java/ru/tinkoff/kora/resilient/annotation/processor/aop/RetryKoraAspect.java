package ru.tinkoff.kora.resilient.annotation.processor.aop;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import ru.tinkoff.kora.annotation.processor.common.MethodUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.aop.annotation.processor.KoraAspect;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.palantir.javapoet.CodeBlock.joining;

public class RetryKoraAspect implements KoraAspect {

    private static final ClassName RETRY_EXCEPTION = ClassName.get("ru.tinkoff.kora.resilient.retry", "RetryExhaustedException");
    private static final ClassName ANNOTATION_TYPE = ClassName.get("ru.tinkoff.kora.resilient.retry.annotation", "Retry");
    private static final ClassName RETRY_STATE = ClassName.get("ru.tinkoff.kora.resilient.retry", "Retry", "RetryState", "RetryStatus");

    private final ProcessingEnvironment env;

    public RetryKoraAspect(ProcessingEnvironment env) {
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
        final String retryableName = mirror.flatMap(a -> a.getElementValues().entrySet().stream()
                .filter(e -> e.getKey().getSimpleName().contentEquals("value"))
                .map(e -> String.valueOf(e.getValue().getValue())).findFirst())
            .orElseThrow();

        final CodeBlock body;
        if (MethodUtils.isFuture(method)) {
            var managerType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement("ru.tinkoff.kora.resilient.retry.RetryManager"));
            var fieldManager = aspectContext.fieldFactory().constructorParam(managerType, List.of());
            var retrierType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement("ru.tinkoff.kora.resilient.retry.Retry"));
            var fieldRetrier = aspectContext.fieldFactory().constructorInitialized(retrierType, CodeBlock.of("$L.get($S)", fieldManager, retryableName));
            body = buildBodyFuture(method, superCall, fieldRetrier);
        } else {
            var managerType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement("ru.tinkoff.kora.resilient.retry.RetryManager"));
            var fieldManager = aspectContext.fieldFactory().constructorParam(managerType, List.of());
            var retrierType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement("ru.tinkoff.kora.resilient.retry.Retry"));
            var fieldRetrier = aspectContext.fieldFactory().constructorInitialized(retrierType, CodeBlock.of("$L.get($S)", fieldManager, retryableName));
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

        builder.add("return $L.retry(() -> $L)", fieldRetry, methodCall);
        if (CompletableFuture.class.getCanonicalName().equals(((DeclaredType) method.getReturnType()).asElement().toString())) {
            builder.add(".toCompletableFuture()");
        }
        builder.add(";\n");
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
