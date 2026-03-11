package io.koraframework.grpc.server.config;

import org.jspecify.annotations.Nullable;
import io.koraframework.common.util.Size;
import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.grpc.server.telemetry.GrpcServerTelemetryConfig;

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

    GrpcServerTelemetryConfig telemetry();

    @Nullable
    Duration maxConnectionAge();

    @Nullable
    Duration maxConnectionAgeGrace();

    @Nullable
    Duration keepAliveTime();

    @Nullable
    Duration keepAliveTimeout();
}
