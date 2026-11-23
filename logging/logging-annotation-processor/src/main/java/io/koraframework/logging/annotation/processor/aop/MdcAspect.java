package io.koraframework.logging.annotation.processor.aop;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.annotation.processor.common.MethodUtils;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.aop.annotation.processor.KoraAspect;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.koraframework.annotation.processor.common.AnnotationUtils.*;
import static io.koraframework.logging.annotation.processor.aop.MdcAspectClassNames.*;

public class MdcAspect implements KoraAspect {

    private static final String MDC_CONTEXT_VAR_NAME = "__mdcContext";

    private static final Set<String> NATIVE_MDC_TYPES = Set.of(
        String.class.getCanonicalName(),
        Integer.class.getCanonicalName(),
        Long.class.getCanonicalName(),
        Boolean.class.getCanonicalName(),
        mdcWriter.canonicalName()
    );

    private static boolean isNativeMdcType(TypeMirror type) {
        return switch (type.getKind()) {
            // primitives autobox to their wrappers, which have dedicated MDC.put overloads
            case INT, LONG, BOOLEAN -> true;
            case DECLARED -> ((DeclaredType) type).asElement() instanceof TypeElement typeElement
                && NATIVE_MDC_TYPES.contains(typeElement.getQualifiedName().toString());
            default -> false;
        };
    }

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(mdcAnnotation, mdcContainerAnnotation);
    }

    @Override
    public ApplyResult apply(ExecutableElement method, String superCall, AspectContext aspectContext) {
        if (MethodUtils.isPublisher(method)) {
            throw new ProcessingErrorException("@%s can't be applied for type ".formatted(mdcAnnotation.simpleName()) + CommonClassNames.publisher, method);
        } else if (MethodUtils.isFuture(method)) {
            throw new ProcessingErrorException("@%s can't be applied for type ".formatted(mdcAnnotation) + method.getReturnType().toString(), method);
        } else if (MethodUtils.isCompletionStage(method)) {
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
        final Map<String, String> previousValueVars = new LinkedHashMap<>();
        fillMdcByMethodAnnotations(methodAnnotations, previousValueVars, currentContextBuilder, fillMdcBuilder);
        fillMdcByParametersAnnotations(parametersWithAnnotation, previousValueVars, currentContextBuilder, fillMdcBuilder);
        final CodeBlock.Builder clearMdcBuilder = CodeBlock.builder();
        clearMdc(previousValueVars, clearMdcBuilder);

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

    private static void fillMdcByMethodAnnotations(List<AnnotationMirror> methodAnnotations, Map<String, String> previousValueVars, CodeBlock.Builder currentContextBuilder, CodeBlock.Builder fillMdcBuilder) {
        for (AnnotationMirror annotation : methodAnnotations) {
            final String key = extractStringParameter(annotation, "key")
                .orElseThrow(() -> new ProcessingErrorException("@Mdc annotation must have 'key' attribute", annotation.getAnnotationType().asElement()));
            final String value = extractStringParameter(annotation, "value")
                .orElseThrow(() -> new ProcessingErrorException("@Mdc annotation must have 'value' attribute", annotation.getAnnotationType().asElement()));
            final Boolean global = parseAnnotationValueWithoutDefault(annotation, "global");

            if (global == null || !global) {
                savePreviousValue(key, previousValueVars, currentContextBuilder);
            }
            if (value.startsWith("${") && value.endsWith("}")) {
                fillMdcBuilder.addStatement("$T.put($S, $L)", mdc, key, value.substring(2, value.length() - 1));
            } else {
                fillMdcBuilder.addStatement("$T.put($S, $S)", mdc, key, value);
            }
        }
    }

    private void fillMdcByParametersAnnotations(List<? extends VariableElement> parametersWithAnnotation, Map<String, String> previousValueVars, CodeBlock.Builder currentContextBuilder, CodeBlock.Builder fillMdcBuilder) {
        for (VariableElement parameter : parametersWithAnnotation) {
            final String parameterName = parameter.getSimpleName().toString();
            final AnnotationMirror firstAnnotation = findAnnotations(parameter, mdcAnnotation, mdcContainerAnnotation)
                .get(0);

            final String key = extractStringParameter(firstAnnotation, "key")
                .or(() -> extractStringParameter(firstAnnotation, "value"))
                .orElse(parameterName);

            final Boolean global = parseAnnotationValueWithoutDefault(firstAnnotation, "global");

            final TypeMirror parameterType = parameter.asType();
            if (isNativeMdcType(parameterType)) {
                fillMdcBuilder.addStatement("$T.put($S, $N)", mdc, key, parameterName);
            } else if (parameterType.getKind().isPrimitive()) {
                fillMdcBuilder.addStatement("$T.put($S, $T.valueOf($N))", mdc, key, String.class, parameterName);
            } else {
                fillMdcBuilder.beginControlFlow("if ($N != null)", parameterName)
                    .addStatement("$T.put($S, $N.toString())", mdc, key, parameterName)
                    .endControlFlow();
            }

            if (global == null || !global) {
                savePreviousValue(key, previousValueVars, currentContextBuilder);
            }
        }
    }

    private static void savePreviousValue(String key, Map<String, String> previousValueVars, CodeBlock.Builder currentContextBuilder) {
        if (!previousValueVars.containsKey(key)) {
            final String varName = "__mdcPrev" + previousValueVars.size();
            previousValueVars.put(key, varName);
            currentContextBuilder.addStatement("var $N = $N.get($S)", varName, MDC_CONTEXT_VAR_NAME, key);
        }
    }

    private static Optional<String> extractStringParameter(AnnotationMirror annotation, String name) {
        final String value = parseAnnotationValueWithoutDefault(annotation, name);
        return Optional.ofNullable(value)
            .filter(s -> !s.isBlank());
    }

    private static void clearMdc(Map<String, String> previousValueVars, CodeBlock.Builder b) {
        previousValueVars.forEach((key, varName) -> {
            b.beginControlFlow("if ($N != null)", varName)
                .addStatement("$T.put($S, $N)", mdc, key, varName)
                .endControlFlow()
                .beginControlFlow("else")
                .addStatement("$T.remove($S)", mdc, key)
                .endControlFlow();
        });
    }
}
