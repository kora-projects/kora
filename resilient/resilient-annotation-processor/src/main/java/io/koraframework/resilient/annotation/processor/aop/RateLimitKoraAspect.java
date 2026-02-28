package ru.tinkoff.kora.resilient.annotation.processor.aop;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
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

import static com.palantir.javapoet.CodeBlock.joining;

public class RateLimitKoraAspect implements KoraAspect {

    private static final ClassName ANNOTATION_TYPE = ClassName.get("ru.tinkoff.kora.resilient.ratelimiter.annotation", "RateLimit");
    private static final ClassName EXCEEDED_EXCEPTION = ClassName.get("ru.tinkoff.kora.resilient.ratelimiter", "RateLimitExceededException");

    private final ProcessingEnvironment env;

    public RateLimitKoraAspect(ProcessingEnvironment env) {
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

        final Optional<? extends AnnotationMirror> mirror = method.getAnnotationMirrors().stream()
            .filter(a -> a.getAnnotationType().toString().equals(ANNOTATION_TYPE.canonicalName()))
            .findFirst();

        final String rateLimiterName = mirror.flatMap(a -> a.getElementValues().entrySet().stream()
                .filter(e -> e.getKey().getSimpleName().contentEquals("value"))
                .map(e -> String.valueOf(e.getValue().getValue()))
                .findFirst())
            .orElseThrow();

        var managerType = env.getTypeUtils().getDeclaredType(
            env.getElementUtils().getTypeElement("ru.tinkoff.kora.resilient.ratelimiter.RateLimiterManager"));
        var fieldManager = aspectContext.fieldFactory().constructorParam(managerType, List.of());
        var rateLimiterType = env.getTypeUtils().getDeclaredType(
            env.getElementUtils().getTypeElement("ru.tinkoff.kora.resilient.ratelimiter.RateLimiter"));
        var fieldRateLimiter = aspectContext.fieldFactory().constructorInitialized(rateLimiterType,
            CodeBlock.of("$L.get($S)", fieldManager, rateLimiterName));

        final CodeBlock body;
        if (MethodUtils.isFuture(method)) {
            body = buildBodyFuture(method, superCall, fieldRateLimiter);
        } else {
            body = buildBodySync(method, superCall, fieldRateLimiter);
        }

        return new ApplyResult.MethodBody(body);
    }

    private CodeBlock buildBodySync(ExecutableElement method, String superCall, String rlField) {
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
                $L;
            } catch ($T _e) {
                throw _e;
            } catch (Throwable _e) {
                throw _e;
            }
            """, rlField, methodCall.toString(), returnCall.toString(), EXCEEDED_EXCEPTION).build();
    }

    private CodeBlock buildBodyFuture(ExecutableElement method, String superCall, String rlField) {
        final CodeBlock superMethod = buildMethodCall(method, superCall);

        return CodeBlock.builder().add("""
            try {
                $L.acquire();
                return $L;
            } catch ($T _e) {
                return $T.failedFuture(_e);
            } catch (Throwable _e) {
                throw _e;
            }
            """, rlField, superMethod, EXCEEDED_EXCEPTION, CompletableFuture.class).build();
    }

    private CodeBlock buildMethodCall(ExecutableElement method, String call) {
        return method.getParameters().stream()
            .map(p -> CodeBlock.of("$L", p))
            .collect(joining(", ", call + "(", ")"));
    }
}
