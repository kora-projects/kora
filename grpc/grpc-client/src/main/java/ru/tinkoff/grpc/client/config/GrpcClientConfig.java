package ru.tinkoff.grpc.client.config;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.time.Duration;
import java.util.Objects;

@ConfigValueExtractor
public interface GrpcClientConfig {
    String url();

    @Nullable
    Duration timeout();

    TelemetryConfig telemetry();

    static GrpcClientConfig defaultConfig(Config config, ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor<GrpcClientConfig> extractor, String serviceName) {
        return Objects.requireNonNull(extractor.extract(config.get("grpcClient." + serviceName)));
    }
}
