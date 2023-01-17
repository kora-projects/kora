package ru.tinkoff.kora.resilient.annotation.processor.aop;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.CodeBlock;
import ru.tinkoff.kora.annotation.processor.common.MethodUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingError;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.aop.annotation.processor.KoraAspect;

import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.tools.Diagnostic;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;

import static com.squareup.javapoet.CodeBlock.joining;

public class TimeoutKoraAspect implements KoraAspect {

    private static final String ANNOTATION_TYPE = "ru.tinkoff.kora.resilient.timeout.annotation.Timeout";

    private final ProcessingEnvironment env;

    public TimeoutKoraAspect(ProcessingEnvironment env) {
        this.env = env;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(ANNOTATION_TYPE);
    }

    @Override
    public ApplyResult apply(ExecutableElement method, String superCall, AspectContext aspectContext) {
        if (MethodUtils.isFuture(method, env)) {
            throw new ProcessingErrorException(new ProcessingError(Diagnostic.Kind.NOTE, "@Timeout can't be applied for types assignable from " + Future.class, method));
        }

        final Optional<? extends AnnotationMirror> mirror = method.getAnnotationMirrors().stream().filter(a -> a.getAnnotationType().toString().equals(ANNOTATION_TYPE)).findFirst();
        final String timeoutName = mirror.flatMap(a -> a.getElementValues().entrySet().stream()
                .filter(e -> e.getKey().getSimpleName().contentEquals("value"))
                .map(e -> String.valueOf(e.getValue().getValue())).findFirst())
            .orElseThrow();

        var managerType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement("ru.tinkoff.kora.resilient.timeout.TimeouterManager"));
        var fieldManager = aspectContext.fieldFactory().constructorParam(managerType, List.of());
        var metricsType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement("ru.tinkoff.kora.resilient.timeout.telemetry.TimeoutMetrics"));
        var fieldMetrics = aspectContext.fieldFactory().constructorParam(metricsType, List.of(AnnotationSpec.builder(Nullable.class).build()));
        var timeouterType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement("ru.tinkoff.kora.resilient.timeout.Timeouter"));
        var fieldTimeouter = aspectContext.fieldFactory().constructorInitialized(timeouterType,
            CodeBlock.of("$L.get($S)", fieldManager, timeoutName));

        final CodeBlock body;
        if (MethodUtils.isMono(method, env)) {
            body = buildBodyMono(method, superCall, timeoutName, fieldTimeouter, fieldMetrics);
        } else if (MethodUtils.isFlux(method, env)) {
            body = buildBodyFlux(method, superCall, timeoutName, fieldTimeouter, fieldMetrics);
        } else {
            body = buildBodySync(method, superCall, fieldTimeouter);
        }

        return new ApplyResult.MethodBody(body);
    }

    private CodeBlock buildBodySync(ExecutableElement method, String superCall, String timeoutName) {
        final CodeBlock superMethod = buildMethodCall(method, superCall);
        if (MethodUtils.isVoid(method)) {
            return CodeBlock.builder().add("""
                $L.execute(() -> $L);
                """, timeoutName, superMethod.toString()).build();
        } else {
            return CodeBlock.builder().add("""
                return $L.execute(() -> $L);
                """, timeoutName, superMethod.toString()).build();
        }
    }

    private CodeBlock buildBodyMono(ExecutableElement method, String superCall, String timeoutName, String fieldTimeout, String fieldMetrics) {
        final CodeBlock superMethod = buildMethodCall(method, superCall);
        return CodeBlock.builder().add("""
            return $L
                .timeout($L.timeout())
                .doOnError(e -> {
                    if(e instanceof java.util.concurrent.TimeoutException && $L != null) {
                        $L.recordTimeout($S, $L.timeout().toNanos());
                    }
                });
                """, superMethod.toString(), fieldTimeout, fieldMetrics, fieldMetrics, timeoutName, fieldTimeout).build();
    }

    private CodeBlock buildBodyFlux(ExecutableElement method, String superCall, String timeoutName, String fieldTimeout, String fieldMetrics) {
        final CodeBlock superMethod = buildMethodCall(method, superCall);
        return CodeBlock.builder().add("""
            return $L
                .timeout($L.timeout())
                .doOnError(e -> {
                    if(e instanceof java.util.concurrent.TimeoutException && $L != null) {
                        $L.recordTimeout($S, $L.timeout().toNanos());
                    }
                });
            """, superMethod.toString(), fieldTimeout, fieldMetrics, fieldMetrics, timeoutName, fieldTimeout).build();
    }

    private CodeBlock buildMethodCall(ExecutableElement method, String call) {
        return method.getParameters().stream().map(p -> CodeBlock.of("$L", p)).collect(joining(", ", call + "(", ")"));
    }
}
