package ru.tinkoff.kora.camunda.zeebe.worker.telemetry;

import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public class DefaultZeebeWorkerLoggerFactory implements ZeebeWorkerLoggerFactory {

    @Override
    public ZeebeWorkerLogger get(TelemetryConfig.LogConfig config) {
        if (Boolean.TRUE.equals(config.enabled())) {
            return new DefaultZeebeWorkerLogger();
        }

        return null;
    }
}
