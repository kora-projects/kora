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

    String url();

    @Nullable
    Duration timeout();

    GrpcClientTelemetryConfig telemetry();

    @Nullable
    DefaultServiceConfig defaultServiceConfig();

    @Nullable
    Duration keepAliveTime();

    @Nullable
    Duration keepAliveTimeout();

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
