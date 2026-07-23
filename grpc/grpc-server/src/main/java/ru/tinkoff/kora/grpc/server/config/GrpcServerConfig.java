package ru.tinkoff.kora.grpc.server.config;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.util.Size;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.time.Duration;

@ConfigValueExtractor
public interface GrpcServerConfig {

    /**
     * @return gRPC server port.
     */
    default int port() {
        return 8090;
    }

    /**
     * @return Enables the gRPC Server Reflection service.
     */
    default boolean reflectionEnabled() {
        return false;
    }

    /**
     * @return Maximum size of an incoming message.
     */
    default Size maxMessageSize() {
        return Size.of(4, Size.Type.MiB);
    }

    /**
     * @return Time to wait for in-flight calls to complete before shutting down the server during graceful shutdown.
     */
    default Duration shutdownWait() {
        return Duration.ofSeconds(30);
    }

    /**
     * @return Telemetry configuration for logging, metrics and tracing of server calls.
     */
    TelemetryConfig telemetry();

    /**
     * @return Maximum connection age after which the connection is gracefully terminated, with a random jitter of +/-10%.
     */
    @Nullable
    Duration maxConnectionAge();

    /**
     * @return Additional time for graceful connection termination after the maximum connection age is reached.
     */
    @Nullable
    Duration maxConnectionAgeGrace();

    /**
     * @return Interval between PING frames.
     */
    @Nullable
    Duration keepAliveTime();

    /**
     * @return Timeout for acknowledging a PING frame, after which the connection is closed.
     */
    @Nullable
    Duration keepAliveTimeout();
}
