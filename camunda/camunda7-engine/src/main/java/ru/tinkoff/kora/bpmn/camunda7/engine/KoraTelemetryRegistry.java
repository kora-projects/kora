package ru.tinkoff.kora.bpmn.camunda7.engine;

import jakarta.annotation.Nullable;
import org.camunda.bpm.engine.impl.telemetry.TelemetryRegistry;
import org.camunda.bpm.engine.impl.telemetry.dto.ApplicationServerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KoraTelemetryRegistry extends TelemetryRegistry {

    private static final String INTEGRATION_NAME = "kora-camunda";

    private static final Logger logger = LoggerFactory.getLogger(KoraTelemetryRegistry.class);

    public KoraTelemetryRegistry(@Nullable ApplicationServerImpl applicationServer) {
        setCamundaIntegration(INTEGRATION_NAME);
        if (applicationServer != null) {
            logger.info("Camunda ApplicationServer: vendor={}, version={}", applicationServer.getVendor(), applicationServer.getVersion());
            setApplicationServer(applicationServer.getVersion());
        } else {
            logger.debug("Unable to identify the ApplicationServer for the Camunda Telemetry Registry");
        }
    }
}
