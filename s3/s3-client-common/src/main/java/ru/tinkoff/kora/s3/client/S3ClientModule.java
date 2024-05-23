package ru.tinkoff.kora.s3.client;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.s3.client.telemetry.*;

public interface S3ClientModule {

    default S3Config s3Config(Config config, ConfigValueExtractor<S3Config> extractor) {
        var value = config.get("s3client");
        return extractor.extract(value);
    }

    @DefaultComponent
    default S3ClientLoggerFactory s3ClientLoggerFactory() {
        return new DefaultS3ClientLoggerFactory();
    }

    @DefaultComponent
    default S3ClientTelemetryFactory s3ClientTelemetryFactory(@Nullable S3ClientLoggerFactory loggerFactory,
                                                              @Nullable S3ClientTracerFactory tracingFactory,
                                                              @Nullable S3ClientMetricsFactory metricsFactory) {
        return new DefaultS3ClientTelemetryFactory(loggerFactory, tracingFactory, metricsFactory);
    }
}
