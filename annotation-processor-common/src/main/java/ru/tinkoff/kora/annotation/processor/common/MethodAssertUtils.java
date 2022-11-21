package ru.tinkoff.kora.annotation.processor.common;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public class MethodAssertUtils {
    public static void assertHasMethod(Class<?> type, String name, Type returnType, Type... params) {
        var methods = Arrays.stream(type.getMethods()).filter(m -> m.getName().equals(name)).toList();
        if (methods.isEmpty()) {
            throw new AssertionError("Type " + type + " was expected to have method " + name + "(" + Arrays.stream(params).map(Objects::toString).collect(Collectors.joining()) + ") but no method with that name found");
        }
        for (var method : methods) {
            if (method.getParameters().length != params.length) {
                continue;
            }
            if (!method.getGenericReturnType().equals(returnType)) {
                break;
            }
            var parameterTypes = method.getGenericParameterTypes();
            for (int i = 0; i < params.length; i++) {
                var expected = params[i];
                var actual = parameterTypes[i];
                if (!expected.equals(actual)) {
                    break;
                }
            }
            return;
        }
        throw new AssertionError("Type " + type + " was expected to have method " + name + "(" + Arrays.stream(params).map(Objects::toString).collect(Collectors.joining()) + ") but no method with same parameters found");
    }

}
