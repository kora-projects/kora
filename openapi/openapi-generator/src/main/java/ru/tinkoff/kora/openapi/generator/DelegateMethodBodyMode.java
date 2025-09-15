package ru.tinkoff.kora.openapi.generator;

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

        var modes = Arrays.stream(CodegenMode.values())
            .map(CodegenMode::getMode)
            .toList();
        throw new UnsupportedOperationException("Unknown DelegateMethodBodyMode is provided: " + option + ", available modes: " + modes);
    }
}
