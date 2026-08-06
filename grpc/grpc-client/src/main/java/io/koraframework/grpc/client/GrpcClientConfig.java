package io.koraframework.grpc.client;

import io.koraframework.config.common.Config;
import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.grpc.client.config.DefaultServiceConfig;
import io.koraframework.grpc.client.telemetry.GrpcClientTelemetryConfig;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

@ConfigMapper
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
    GrpcClientTelemetryConfig telemetry();

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

    static GrpcClientConfig defaultConfig(Config config, ConfigValueMapper<GrpcClientConfig> mapper, String serviceName) {
        var packageEnding = serviceName.lastIndexOf('.');
        var serviceSimpleName = (packageEnding == -1)
            ? serviceName
            : serviceName.substring(packageEnding + 1);

        return mapper.mapOrThrow(config.get("grpcClient." + serviceSimpleName));
    }
}
