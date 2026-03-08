package io.koraframework.grpc.client.config;

import org.jspecify.annotations.Nullable;
import io.koraframework.grpc.client.telemetry.GrpcClientTelemetryConfig;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;
import java.util.Objects;

@ConfigValueExtractor
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

    static GrpcClientConfig defaultConfig(Config config, io.koraframework.config.common.extractor.ConfigValueExtractor<GrpcClientConfig> extractor, String serviceName) {
        var packageEnding = serviceName.lastIndexOf('.');
        var serviceSimpleName = (packageEnding == -1)
            ? serviceName
            : serviceName.substring(packageEnding + 1);

        return Objects.requireNonNull(extractor.extract(config.get("grpcClient." + serviceSimpleName)));
    }
}
