package ru.tinkoff.kora.resilient.annotation.processor.aop;

import ru.tinkoff.kora.annotation.processor.common.ProcessingError;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;

import javax.annotation.Nonnull;
import javax.lang.model.element.ExecutableElement;
import javax.tools.Diagnostic;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

record FallbackMeta(String method, List<String> arguments) {

    static FallbackMeta ofFallbackMethod(@Nonnull String fallbackSignature, @Nonnull ExecutableElement sourceMethod) {
        final int argStarted = fallbackSignature.indexOf('(');
        final int argEnd = fallbackSignature.indexOf(')');
        if (argStarted == -1 || argEnd == -1) {
            throw new ProcessingErrorException(new ProcessingError(
                Diagnostic.Kind.ERROR,
                "Fallback method doesn't have proper signature like 'myMethod()' or 'myMethod(arg1, arg2)' but was: " + fallbackSignature,
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
                    "Fallback method specifies illegal arguments " + illegalArgs + ", available arguments: " + sourceArgs,
                    sourceMethod));
            }
        }

        return new FallbackMeta(fallbackSignature.substring(0, argStarted), fallbackArgs);
    }

    public String call() {
        return toString();
    }

    @Override
    public String toString() {
        return method + "(" + String.join(", ", arguments) + ")";
    }
}
