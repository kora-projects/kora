package io.koraframework.logging.aspect.mdc;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.annotation.processor.common.MethodUtils;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.aop.annotation.processor.KoraAspect;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;

import static io.koraframework.annotation.processor.common.AnnotationUtils.*;
import static io.koraframework.logging.aspect.LogAspectClassNames.log;
import static io.koraframework.logging.aspect.mdc.MdcAspectClassNames.*;

public class MdcAspect implements KoraAspect {

    private static final String MDC_CONTEXT_VAR_NAME = "__mdcContext";

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(mdcAnnotation, mdcContainerAnnotation);
    }

    @Override
    public ApplyResult apply(ExecutableElement method, String superCall, AspectContext aspectContext) {
        if (MethodUtils.isPublisher(method)) {
            throw new ProcessingErrorException("@%s can't be applied for type ".formatted(mdcAnnotation.simpleName()) + CommonClassNames.publisher, method);
        } else if(MethodUtils.isFuture(method)) {
            throw new ProcessingErrorException("@%s can't be applied for type ".formatted(mdcAnnotation) + method.getReturnType().toString(), method);
        }

        final List<AnnotationMirror> methodAnnotations = findAnnotations(method, mdcAnnotation, mdcContainerAnnotation);

        final List<? extends VariableElement> parametersWithAnnotation = method.getParameters()
            .stream()
            .filter(param -> isAnnotationPresent(param, mdcAnnotation))
            .toList();

        if (methodAnnotations.isEmpty() && parametersWithAnnotation.isEmpty()) {
            final CodeBlock code = CodeBlock.builder()
                .add(MethodUtils.isVoid(method) ? "" : "return ")
                .addStatement(KoraAspect.callSuper(method, superCall))
                .build();
            return new ApplyResult.MethodBody(code);
        }

        final CodeBlock.Builder currentContextBuilder = CodeBlock.builder();
        currentContextBuilder.addStatement("var $N = $T.get().values()", MDC_CONTEXT_VAR_NAME, mdc);
        final CodeBlock.Builder fillMdcBuilder = CodeBlock.builder();
        final Set<String> methodKeys = fillMdcByMethodAnnotations(methodAnnotations, currentContextBuilder, fillMdcBuilder);
        final Set<String> parametersKeys = fillMdcByParametersAnnotations(parametersWithAnnotation, currentContextBuilder, fillMdcBuilder);
        final CodeBlock.Builder clearMdcBuilder = CodeBlock.builder();
        clearMdc(methodKeys, clearMdcBuilder);
        clearMdc(parametersKeys, clearMdcBuilder);

        final CodeBlock code = CodeBlock.builder()
            .add(currentContextBuilder.build())
            .beginControlFlow("try")
            .add(fillMdcBuilder.build())
            .add(MethodUtils.isVoid(method) ? "" : "return ")
            .addStatement(KoraAspect.callSuper(method, superCall))
            .nextControlFlow("finally")
            .add(clearMdcBuilder.build())
            .endControlFlow()
            .build();

        return new ApplyResult.MethodBody(code);
    }

    private static Set<String> fillMdcByMethodAnnotations(List<AnnotationMirror> methodAnnotations, CodeBlock.Builder currentContextBuilder, CodeBlock.Builder fillMdcBuilder) {
        final Set<String> keys = new HashSet<>();
        for (AnnotationMirror annotation : methodAnnotations) {
            final String key = extractStringParameter(annotation, "key")
                .orElseThrow(() -> new ProcessingErrorException("@Mdc annotation must have 'key' attribute", annotation.getAnnotationType().asElement()));
            final String value = extractStringParameter(annotation, "value")
                .orElseThrow(() -> new ProcessingErrorException("@Mdc annotation must have 'value' attribute", annotation.getAnnotationType().asElement()));
            final Boolean global = parseAnnotationValueWithoutDefault(annotation, "global");

            if (global == null || !global) {
                keys.add(key);
                currentContextBuilder.addStatement("var __$N = $N.get($S)", key, MDC_CONTEXT_VAR_NAME, key);
            }
            if (value.startsWith("${") && value.endsWith("}")) {
                fillMdcBuilder.addStatement("$T.put($S, $L)", mdc, key, value.substring(2, value.length() - 1));
            } else {
                fillMdcBuilder.addStatement("$T.put($S, $S)", mdc, key, value);
            }
        }
        return keys;
    }

    private Set<String> fillMdcByParametersAnnotations(List<? extends VariableElement> parametersWithAnnotation, CodeBlock.Builder currentContextBuilder, CodeBlock.Builder fillMdcBuilder) {
        final Set<String> keys = new HashSet<>();
        for (VariableElement parameter : parametersWithAnnotation) {
            final String parameterName = parameter.getSimpleName().toString();
            final AnnotationMirror firstAnnotation = findAnnotations(parameter, mdcAnnotation, mdcContainerAnnotation)
                .get(0);

            final String key = extractStringParameter(firstAnnotation, "key")
                .or(() -> extractStringParameter(firstAnnotation, "value"))
                .orElse(parameterName);

            final Boolean global = parseAnnotationValueWithoutDefault(firstAnnotation, "global");

            fillMdcBuilder.addStatement(
                "$T.put($S, $N)",
                mdc,
                key,
                parameterName
            );

            if (global == null || !global) {
                keys.add(key);
                currentContextBuilder.addStatement("var __$N = $N.get($S)", key, MDC_CONTEXT_VAR_NAME, key);
            }
        }
        return keys;
    }

    private static Optional<String> extractStringParameter(AnnotationMirror annotation, String name) {
        final String value = parseAnnotationValueWithoutDefault(annotation, name);
        return Optional.ofNullable(value)
            .filter(s -> !s.isBlank());
    }

    private static void clearMdc(Set<String> keys, CodeBlock.Builder b) {
        for (String key : keys) {
            b.beginControlFlow("if (__$N != null)", key)
                .addStatement("$T.put($S, __$N)", mdc, key, key)
                .endControlFlow()
                .beginControlFlow("else")
                .addStatement("$T.remove($S)", mdc, key)
                .endControlFlow();
        }
    }
}
