package ru.tinkoff.kora.bpmn.camunda7.engine;

import jakarta.annotation.Nullable;
import org.camunda.bpm.engine.ProcessEngine;

import java.util.Optional;

public record CamundaVersion(@Nullable String version) {

    public boolean isEnterprise() {
        String version = version();
        if (version == null) {
            return false;
        }

        return version.contains("-ee");
    }
}
