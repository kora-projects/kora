package io.koraframework.openapi.generator;

public enum ResponseStyle {
    SEALED,
    PLAIN;

    static ResponseStyle of(String value) {
        return switch (value.toLowerCase()) {
            case "plain" -> PLAIN;
            default -> SEALED;
        };
    }
}
