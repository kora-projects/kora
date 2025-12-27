package ru.tinkoff.kora.camunda.engine.bpmn;

import org.jspecify.annotations.Nullable;

public record CamundaVersion(@Nullable String version) {

    public boolean isEnterprise() {
        String version = version();
        if (version == null) {
            return false;
        }

        return version.contains("-ee");
    }
}
