package ru.tinkoff.kora.grpc.server.config;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.util.Size;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.time.Duration;

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

    default Duration shutdownWait() {
        return Duration.ofSeconds(30);
    }

    TelemetryConfig telemetry();

    @Nullable
    Duration maxConnectionAge();

    @Nullable
    Duration maxConnectionAgeGrace();

    @Nullable
    Duration keepAliveTime();

    @Nullable
    Duration keepAliveTimeout();
}
