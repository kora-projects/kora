package ru.tinkoff.kora.grpc.server.config;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface GrpcServerConfig {

    default int port() {
        return 8090;
    }

    default boolean reflectionEnabled() {
        return false;
    }

    TelemetryConfig telemetry();
}
