package io.koraframework.openapi.generator;

import java.util.Arrays;

public enum DelegateMethodBodyMode {
    NONE("none"),
    THROW_EXCEPTION("throwException");

    private final String mode;

    DelegateMethodBodyMode(String mode) {
        this.mode = mode;
    }

    public String getMode() {
        return mode;
    }

    public static DelegateMethodBodyMode of(String option) {
        for (var value : DelegateMethodBodyMode.values()) {
            if (value.getMode().equals(option)) {
                return value;
            }
        }

        var modes = Arrays.stream(DelegateMethodBodyMode.values())
            .map(DelegateMethodBodyMode::getMode)
            .toList();
        throw new UnsupportedOperationException("""
            Invalid OpenAPI generator `delegateMethodBodyMode`: `%s`.

            Supported modes: %s

            Fix: set generator option `delegateMethodBodyMode` to one of the supported values.
            """.formatted(option, modes));
    }
}
