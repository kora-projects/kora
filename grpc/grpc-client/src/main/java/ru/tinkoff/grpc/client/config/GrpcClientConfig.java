package ru.tinkoff.grpc.client.config;

import jakarta.annotation.Nullable;
import ru.tinkoff.grpc.client.telemetry.GrpcClientTelemetryConfig;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

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

    static GrpcClientConfig defaultConfig(Config config, ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor<GrpcClientConfig> extractor, String serviceName) {
        var packageEnding = serviceName.lastIndexOf('.');
        var serviceSimpleName = (packageEnding == -1)
            ? serviceName
            : serviceName.substring(packageEnding + 1);

        return Objects.requireNonNull(extractor.extract(config.get("grpcClient." + serviceSimpleName)));
    }
}
