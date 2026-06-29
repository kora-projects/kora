package io.koraframework.resilient.timeout;

import io.koraframework.resilient.timeout.telemetry.TimeoutTelemetryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class KoraTimeoutManager implements TimeoutManager {

    private static final Logger logger = LoggerFactory.getLogger(KoraTimeoutManager.class);

    private final Map<String, Timeout> timeouterMap = new ConcurrentHashMap<>();
    private final TimeoutConfig config;
    private final TimeoutTelemetryFactory telemetryFactory;

    KoraTimeoutManager(TimeoutConfig config, TimeoutTelemetryFactory telemetryFactory) {
        this.config = config;
        this.telemetryFactory = telemetryFactory;
    }

    @Override
    public Timeout get(String name) {
        return timeouterMap.computeIfAbsent(name, (k) -> {
            var config = this.config.getNamedConfig(name);
            logger.atDebug()
                .addKeyValue("resilientType", "timeout")
                .addKeyValue("resilientName", name)
                .addKeyValue("config", config)
                .log("Creating Timeout");
            return new KoraTimeout(name, config.duration(), this.telemetryFactory.get(name, this.config.telemetry()), config);
        });
    }
}
