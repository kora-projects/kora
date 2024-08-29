package ru.tinkoff.kora.s3.client;

import jakarta.annotation.Nullable;
import org.jetbrains.annotations.ApiStatus;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.s3.client.telemetry.*;

@ApiStatus.Experimental
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

    @DefaultComponent
    default S3KoraClientLoggerFactory s3KoraClientLoggerFactory() {
        return new DefaultS3KoraClientLoggerFactory();
    }

    @DefaultComponent
    default S3KoraClientTelemetryFactory s3KoraClientTelemetryFactory(@Nullable S3KoraClientLoggerFactory loggerFactory,
                                                                      @Nullable S3KoraClientTracerFactory tracingFactory,
                                                                      @Nullable S3KoraClientMetricsFactory metricsFactory) {
        return new DefaultS3KoraClientTelemetryFactory(loggerFactory, tracingFactory, metricsFactory);
    }
}
