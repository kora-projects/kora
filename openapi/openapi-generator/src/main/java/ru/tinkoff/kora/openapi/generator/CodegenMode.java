package ru.tinkoff.kora.openapi.generator;

import java.util.Arrays;

public enum CodegenMode {
    JAVA_CLIENT("java-client"),
    JAVA_SERVER("java-server"),
    KOTLIN_CLIENT("kotlin-client"),
    KOTLIN_SERVER("kotlin-server");

    private final String mode;

    CodegenMode(String mode) {
        this.mode = mode;
    }

    public String getMode() {
        return mode;
    }

    public static CodegenMode ofMode(String option) {
        for (var value : CodegenMode.values()) {
            if (value.getMode().equals(option)) {
                return value;
            }
        }
        var modes = Arrays.stream(CodegenMode.values())
            .map(CodegenMode::getMode)
            .toList();
        throw new UnsupportedOperationException("Unknown Mode is provided: " + option + ", available modes: " + modes);
    }

    public boolean isJava() {
        return this == JAVA_CLIENT || this == JAVA_SERVER;
    }

    public boolean isKotlin() {
        return this == KOTLIN_CLIENT || this == KOTLIN_SERVER;
    }

    public boolean isClient() {
        return this == KOTLIN_CLIENT || this == JAVA_CLIENT;
    }

    public boolean isServer() {
        return this == JAVA_SERVER || this == KOTLIN_SERVER;
    }
}
