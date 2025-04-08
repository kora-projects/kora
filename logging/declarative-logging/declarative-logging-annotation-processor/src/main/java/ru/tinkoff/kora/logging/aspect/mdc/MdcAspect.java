package ru.tinkoff.kora.logging.aspect.mdc;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.MethodUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.aop.annotation.processor.KoraAspect;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;

import static java.util.function.Predicate.not;
import static ru.tinkoff.kora.annotation.processor.common.AnnotationUtils.findAnnotations;
import static ru.tinkoff.kora.annotation.processor.common.AnnotationUtils.isAnnotationPresent;
import static ru.tinkoff.kora.logging.aspect.mdc.MdcAspectClassNames.mdc;
import static ru.tinkoff.kora.logging.aspect.mdc.MdcAspectClassNames.mdcAnnotation;
import static ru.tinkoff.kora.logging.aspect.mdc.MdcAspectClassNames.mdcContainerAnnotation;

public class MdcAspect implements KoraAspect {

    private static final String MDC_CONTEXT_VAR_NAME = "__mdcContext";

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(mdcAnnotation.canonicalName(), mdcContainerAnnotation.canonicalName());
    }

    @Override
    public ApplyResult apply(ExecutableElement executableElement, String superCall, AspectContext aspectContext) {
        final List<AnnotationMirror> methodAnnotations = findAnnotations(executableElement, mdcAnnotation, mdcContainerAnnotation);

        final List<? extends VariableElement> parametersWithAnnotation = executableElement.getParameters()
            .stream()
            .filter(param -> isAnnotationPresent(param, mdcAnnotation))
            .toList();

        if (methodAnnotations.isEmpty() && parametersWithAnnotation.isEmpty()) {
            final CodeBlock code = CodeBlock.builder()
                .add(MethodUtils.isVoid(executableElement) ? "" : "return ")
                .addStatement(KoraAspect.callSuper(executableElement, superCall))
                .build();
            return new ApplyResult.MethodBody(code);
        }
        if (MethodUtils.isFuture(executableElement)) {
            throw new ProcessingErrorException("@Mdc can't be applied for types assignable from " + ClassName.get(Future.class), executableElement);
        }
        if (MethodUtils.isMono(executableElement) || MethodUtils.isFlux(executableElement)) {
            throw new ProcessingErrorException("@Mdc can't be applied for types assignable from " + CommonClassNames.publisher, executableElement);
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
            .add(MethodUtils.isVoid(executableElement) ? "" : "return ")
            .addStatement(KoraAspect.callSuper(executableElement, superCall))
            .nextControlFlow("finally")
            .add(clearMdcBuilder.build())
            .endControlFlow()
            .build();

        return new ApplyResult.MethodBody(code);
    }

    private static Set<String> fillMdcByMethodAnnotations(List<AnnotationMirror> methodAnnotations, CodeBlock.Builder currentContextBuilder, CodeBlock.Builder fillMdcBuilder) {
        final Set<String> keys = new HashSet<>();
        for (AnnotationMirror annotation : methodAnnotations) {
            final String key = extractParameter(annotation, String.class, "key")
                .orElseThrow(() -> new ProcessingErrorException("@Mdc annotation must have 'key' attribute", annotation.getAnnotationType().asElement()));
            final String value = extractParameter(annotation, String.class, "value")
                .orElseThrow(() -> new ProcessingErrorException("@Mdc annotation must have 'value' attribute", annotation.getAnnotationType().asElement()));
            final boolean global = extractParameter(annotation, Boolean.class, "global")
                .orElse(false);

            if (!global) {
                keys.add(key);
                currentContextBuilder.addStatement("var __$N = $N.get($S)", key, MDC_CONTEXT_VAR_NAME, key);
            }
            if (value.startsWith("${") && value.endsWith("}")) {
                fillMdcBuilder.addStatement("$T.put($S, $N)", mdc, key, value.substring(2, value.length() - 1));
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

            final String key = extractParameter(firstAnnotation, String.class, "key")
                .filter(not(String::isEmpty))
                .or(() -> extractParameter(firstAnnotation, String.class, "value"))
                .filter(not(String::isEmpty))
                .orElse(parameterName);

            final boolean global = extractParameter(firstAnnotation, Boolean.class, "global")
                .orElse(false);

            fillMdcBuilder.addStatement(
                "$T.put($S, $N)",
                mdc,
                key,
                parameterName
            );

            if (!global) {
                keys.add(key);
                currentContextBuilder.addStatement("var __$N = $N.get($S)", key, MDC_CONTEXT_VAR_NAME, key);
            }
        }
        return keys;
    }

    private static <T> Optional<T> extractParameter(AnnotationMirror annotation, Class<T> type, String name) {
        return annotation.getElementValues()
            .entrySet()
            .stream()
            .filter(entry -> entry.getKey().getSimpleName().toString().contains(name))
            .findFirst()
            .map(entry -> entry.getValue().getValue())
            .map(type::cast);
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
