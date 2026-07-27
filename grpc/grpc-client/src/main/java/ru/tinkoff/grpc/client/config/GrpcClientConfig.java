package ru.tinkoff.grpc.client.config;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.time.Duration;
import java.util.Objects;

@ConfigValueExtractor
public interface GrpcClientConfig {
    /**
     * @return Server URL where requests will be sent.
     */
    String url();

    /**
     * @return Maximum request execution time, applied as a call deadline if the call does not already have its own.
     */
    @Nullable
    Duration timeout();

    /**
     * @return Telemetry configuration for logging, metrics and tracing of client calls.
     */
    TelemetryConfig telemetry();

    /**
     * @return Standard gRPC service configuration passed to ManagedChannelBuilder.defaultServiceConfig.
     */
    @Nullable
    DefaultServiceConfig defaultServiceConfig();

    /**
     * @return Interval between gRPC PING frames.
     */
    @Nullable
    Duration keepAliveTime();

    /**
     * @return Timeout for acknowledging a PING frame, after which the connection is closed.
     */
    @Nullable
    Duration keepAliveTimeout();

    /**
     * @return Load balancing policy for ManagedChannelBuilder.
     */
    @Nullable
    String loadBalancingPolicy();

    static GrpcClientConfig defaultConfig(Config config, ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor<GrpcClientConfig> extractor, String serviceName) {
        var packageEnding = serviceName.lastIndexOf('.');
        var serviceSimpleName = (packageEnding == -1)
            ? serviceName
            : serviceName.substring(packageEnding + 1);

        return Objects.requireNonNull(extractor.extract(config.get("grpcClient." + serviceSimpleName)));
    }
}
