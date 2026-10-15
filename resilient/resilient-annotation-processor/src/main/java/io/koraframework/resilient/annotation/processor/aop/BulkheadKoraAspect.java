package io.koraframework.resilient.annotation.processor.aop;

import static com.palantir.javapoet.CodeBlock.joining;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import io.koraframework.annotation.processor.common.CommonUtils;
import io.koraframework.annotation.processor.common.MethodUtils;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.aop.annotation.processor.KoraAspect;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

public class BulkheadKoraAspect implements KoraAspect {

    private static final ClassName ANNOTATION = ClassName.get("io.koraframework.resilient.bulkhead.annotation", "Bulkheaded");
    private static final ClassName BULKHEAD = ClassName.get("io.koraframework.resilient.bulkhead", "Bulkhead");
    private final ProcessingEnvironment env;

    public BulkheadKoraAspect(ProcessingEnvironment env) {
        this.env = env;
    }

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() { return Set.of(ANNOTATION); }

    @Override
    public ApplyResult apply(ExecutableElement method, String superCall, AspectContext context) {
        var returnType = env.getTypeUtils().erasure(method.getReturnType()).toString();
        if (MethodUtils.isPublisher(method) || MethodUtils.isFuture(method) || returnType.equals("java.util.concurrent.Flow.Publisher")
                || CommonUtils.doesImplement(method.getReturnType(), ClassName.get("java.util.concurrent", "Flow", "Publisher"))) {
            throw new ProcessingErrorException(
                ResilientAopErrors.unsupportedReturnTypeError("@Bulkheaded", method, method.getReturnType()), method
            );
        }
        var stage = MethodUtils.isCompletionStage(method);
        if (stage && !returnType.equals("java.util.concurrent.CompletionStage")
                && !returnType.equals("java.util.concurrent.CompletableFuture")) {
            throw new ProcessingErrorException(
                ResilientAopErrors.unsupportedReturnTypeError("@Bulkheaded", method, method.getReturnType()), method
            );
        }
        var mirror = method.getAnnotationMirrors()
            .stream()
            .filter(annotation -> annotation.getAnnotationType().toString().equals(ANNOTATION.canonicalName()))
            .findFirst()
            .orElseThrow();
        var type = mirror.getElementValues()
            .entrySet()
            .stream()
            .filter(entry -> entry.getKey().getSimpleName().contentEquals("value"))
            .map(entry -> (TypeMirror) entry.getValue().getValue())
            .findFirst()
            .orElseThrow();
        if (!env.getTypeUtils().isAssignable(type, env.getElementUtils().getTypeElement(BULKHEAD.canonicalName()).asType())) {
            throw new ProcessingErrorException(
                ResilientAopErrors.invalidResilientContractError("@Bulkheaded", method, BULKHEAD.canonicalName()), method
            );
        }
        var field = context.fieldFactory().constructorParam(type, List.of());
        var call = method.getParameters()
            .stream()
            .map(parameter -> CodeBlock.of("$L", parameter))
            .collect(joining(", ", superCall + "(", ")"));
        var body = CodeBlock.builder();
        if (stage) {
            body.add("return $L.executeAsync(() -> $L)", field, call);
            if (returnType.equals("java.util.concurrent.CompletableFuture")) {
                body.add(".toCompletableFuture()");
            }
            body.add(";\n");
        } else {
            if (MethodUtils.isVoid(method)) {
                body.addStatement("$L.execute(() -> $L)", field, call);
            } else {
                body.addStatement("return $L.execute(() -> $L)", field, call);
            }
        }
        return new ApplyResult.MethodBody(body.build());
    }
}
