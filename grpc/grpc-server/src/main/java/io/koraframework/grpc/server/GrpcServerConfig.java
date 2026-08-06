package io.koraframework.grpc.server;

import org.jspecify.annotations.Nullable;
import io.koraframework.common.util.Size;
import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.grpc.server.telemetry.GrpcServerTelemetryConfig;

import java.time.Duration;

@ConfigMapper
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
    GrpcServerTelemetryConfig telemetry();

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
