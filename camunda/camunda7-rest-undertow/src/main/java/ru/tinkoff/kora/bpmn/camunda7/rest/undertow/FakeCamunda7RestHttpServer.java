package ru.tinkoff.kora.bpmn.camunda7.rest.undertow;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.bpmn.camunda7.rest.Camunda7RestHttpServer;
import ru.tinkoff.kora.common.readiness.ReadinessProbeFailure;

record FakeCamunda7RestHttpServer(int port) implements Camunda7RestHttpServer {

    @Override
    public void init() {
        // do nothing
    }

    @Override
    public void release() {
        // do nothing
    }

    @Nullable
    @Override
    public ReadinessProbeFailure probe() {
        return null;
    }
}
