package io.koraframework.resilient.annotation.processor.aop;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.ProcessingError;
import io.koraframework.annotation.processor.common.ProcessingErrorException;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

record FallbackMeta(String method, List<String> arguments, TypeMirror reasonType) {

    private static final ClassName REASON = ClassName.get("io.koraframework.resilient.fallback.annotation", "Fallback", "Reason");

    static FallbackMeta ofFallbackMethod(String fallbackSignature, ExecutableElement sourceMethod, ProcessingEnvironment env) {
        final int argStarted = fallbackSignature.indexOf('(');
        final int argEnd = fallbackSignature.indexOf(')');
        if (argStarted == -1 || argEnd == -1) {
            throw new ProcessingErrorException(new ProcessingError(
                Diagnostic.Kind.ERROR,
                """
                @Fallback method reference '%s' has invalid syntax.

                Fix: use method reference syntax 'methodName()' or 'methodName(arg1, arg2)'.
                Example: @Fallback(method = "fallback(value)")
                """.formatted(fallbackSignature).trim(),
                sourceMethod));
        }

        final Set<String> sourceArgs = sourceMethod.getParameters().stream()
            .map(p -> p.getSimpleName().toString())
            .collect(Collectors.toSet());

        final List<String> fallbackArgs = Arrays.stream(fallbackSignature.substring(argStarted + 1, fallbackSignature.length() - 1).split(","))
            .map(String::trim)
            .filter(a -> !a.isEmpty())
            .toList();

        if (!fallbackArgs.isEmpty()) {
            final List<String> illegalArgs = fallbackArgs.stream()
                .filter(a -> !sourceArgs.contains(a))
                .toList();

            if (!illegalArgs.isEmpty()) {
                throw new ProcessingErrorException(new ProcessingError(
                    Diagnostic.Kind.ERROR,
                    """
                    @Fallback method reference '%s' uses unknown source arguments: %s.

                    Available arguments on '%s': %s.
                    Fix: use only parameters declared by the annotated method, or remove the arguments from the fallback reference.
                    """.formatted(fallbackSignature, illegalArgs, sourceMethod.getSimpleName(), sourceArgs).trim(),
                    sourceMethod));
            }
        }

        var methodName = fallbackSignature.substring(0, argStarted);
        var reasonType = findReasonType(methodName, fallbackArgs, sourceMethod, env);
        return new FallbackMeta(methodName, fallbackArgs, reasonType);
    }

    private static TypeMirror findReasonType(String methodName, List<String> fallbackArgs, ExecutableElement sourceMethod, ProcessingEnvironment env) {
        var fallbackMethods = ElementFilter.methodsIn(sourceMethod.getEnclosingElement().getEnclosedElements()).stream()
            .filter(m -> m.getSimpleName().contentEquals(methodName))
            .toList();
        if (fallbackMethods.isEmpty()) {
            throw new ProcessingErrorException(new ProcessingError(Diagnostic.Kind.ERROR, """
                @Fallback method '%s' was not found in '%s'.

                Fix: declare a fallback method with this name in the same class as '%s', or update @Fallback(method = "...") to the existing method name.
                """.formatted(methodName, sourceMethod.getEnclosingElement(), sourceMethod.getSimpleName()).trim(), sourceMethod));
        }

        var throwable = env.getElementUtils().getTypeElement(Throwable.class.getCanonicalName()).asType();
        var exception = env.getElementUtils().getTypeElement(Exception.class.getCanonicalName()).asType();
        var runtimeException = env.getElementUtils().getTypeElement(RuntimeException.class.getCanonicalName()).asType();
        TypeMirror expectedReasonType = runtimeException;
        if (sourceMethod.getThrownTypes().stream().anyMatch(t -> env.getTypeUtils().isSameType(t, throwable))) {
            expectedReasonType = throwable;
        } else if (!sourceMethod.getThrownTypes().isEmpty()) {
            expectedReasonType = exception;
        }

        for (var fallbackMethod : fallbackMethods) {
            var reasonParameters = fallbackMethod.getParameters().stream()
                .filter(p -> AnnotationUtils.isAnnotationPresent(p, REASON))
                .toList();
            if (reasonParameters.size() > 1) {
                throw new ProcessingErrorException(new ProcessingError(
                    Diagnostic.Kind.ERROR,
                    """
                    @Fallback method '%s' declares more than one @Fallback.Reason parameter.

                    Fix: keep at most one @Fallback.Reason parameter. It receives the exception that triggered fallback.
                    """.formatted(fallbackMethod.getSimpleName()).trim(),
                    fallbackMethod));
            }
            if (fallbackMethod.getParameters().size() != fallbackArgs.size() + reasonParameters.size()) {
                continue;
            }
            if (reasonParameters.isEmpty()) {
                return null;
            }
            var reasonParameter = reasonParameters.get(0);
            var reasonType = reasonParameter.asType();
            if (!env.getTypeUtils().isSameType(reasonType, expectedReasonType)) {
                throw new ProcessingErrorException(new ProcessingError(
                    Diagnostic.Kind.ERROR,
                    """
                    @Fallback.Reason parameter on fallback method '%s' has incompatible type '%s'.

                    Expected: %s.
                    Fix: change the @Fallback.Reason parameter type to the expected exception type for the annotated method.
                    """.formatted(fallbackMethod.getSimpleName(), reasonType, expectedReasonType).trim(),
                    reasonParameter));
            }
            return reasonType;
        }

        throw new ProcessingErrorException(new ProcessingError(
            Diagnostic.Kind.ERROR,
            """
            @Fallback method '%s' does not match requested signature '%s(%s)'.

            Fix: make the fallback method accept exactly the referenced source arguments, plus optionally one @Fallback.Reason parameter.
            """.formatted(methodName, methodName, String.join(", ", fallbackArgs)).trim(),
            sourceMethod));
    }

    public String call() {
        return call("_e");
    }

    public String call(String reason) {
        var args = new java.util.ArrayList<>(arguments);
        if (reasonType != null) {
            args.add("((" + TypeName.get(reasonType) + ") " + reason + ")");
        }
        return method + "(" + String.join(", ", args) + ")";
    }

    public boolean hasReason() {
        return reasonType != null;
    }

    @Override
    public String toString() {
        return call();
    }
}
