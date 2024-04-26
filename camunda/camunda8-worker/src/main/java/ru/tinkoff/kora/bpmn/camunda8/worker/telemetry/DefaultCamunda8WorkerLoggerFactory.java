package ru.tinkoff.kora.bpmn.camunda8.worker.telemetry;

import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public class DefaultCamunda8WorkerLoggerFactory implements Camunda8WorkerLoggerFactory {

    @Override
    public Camunda8WorkerLogger get(TelemetryConfig.LogConfig config) {
        if (Boolean.TRUE.equals(config.enabled())) {
            return new DefaultCamunda8WorkerLogger();
        }

        return null;
    }
}
