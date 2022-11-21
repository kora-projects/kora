package ru.tinkoff.kora.cache.annotation.processor;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record CacheMeta(Type type, List<Manager> managers, List<String> parameters, Origin origin) {

    public record Manager(String name, List<String> tags) {}

    public record Origin(String className, String methodName) {

        @Override
        public String toString() {
            return "[class=" + className + ", method=" + methodName + ']';
        }
    }

    public enum Type {
        GET,
        PUT,
        EVICT,
        EVICT_ALL
    }

    public List<String> getParametersNames(ExecutableElement method) {
        return getParameters(method).stream()
            .map(p -> p.getSimpleName().toString())
            .toList();
    }

    public List<VariableElement> getParameters(ExecutableElement method) {
        if (parameters.isEmpty()) {
            return method.getParameters().stream()
                .filter(this::isParameterSupported)
                .map(p -> ((VariableElement) p))
                .toList();

        } else {
            final List<VariableElement> methodParameters = new ArrayList<>();
            for (String parameter : parameters) {
                final Optional<? extends VariableElement> arg = method.getParameters().stream()
                    .filter(p -> p.getSimpleName().contentEquals(parameter))
                    .findFirst();

                if (arg.isPresent()) {
                    methodParameters.add(arg.get());
                } else {
                    throw new IllegalArgumentException("Specified CacheKey parameter '" + parameter + "' is not present in method signature: " + origin());
                }
            }

            return methodParameters;
        }
    }

    public boolean isParameterSupported(VariableElement parameter) {
        return parameters.isEmpty() || parameters.contains(parameter.toString());
    }
}
