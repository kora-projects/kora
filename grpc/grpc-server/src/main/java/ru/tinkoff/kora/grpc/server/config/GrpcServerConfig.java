package ru.tinkoff.kora.grpc.server.config;

import ru.tinkoff.kora.common.util.Size;
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

    default Size maxMessageSize() {
        return Size.of(4, Size.Type.MiB);
    }

    TelemetryConfig telemetry();
}
