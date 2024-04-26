package ru.tinkoff.kora.bpmn.camunda7.engine;

import jakarta.annotation.Nullable;

public record Camunda7Version(@Nullable String version) {

    public boolean isEnterprise() {
        String version = version();
        if (version == null) {
            return false;
        }

        return version.contains("-ee");
    }
}
