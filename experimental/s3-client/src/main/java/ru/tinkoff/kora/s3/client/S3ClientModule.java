package ru.tinkoff.kora.s3.client;

import jakarta.annotation.Nullable;
import org.jetbrains.annotations.ApiStatus;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.http.client.common.HttpClient;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetryFactory;
import ru.tinkoff.kora.s3.client.impl.S3ClientImpl;
import ru.tinkoff.kora.s3.client.telemetry.*;

@ApiStatus.Experimental
public interface S3ClientModule {
    @DefaultComponent
    default S3ClientFactory s3ClientFactory(HttpClient httpClient, S3Config config, S3TelemetryFactory telemetryFactory, HttpClientTelemetryFactory httpClientTelemetryFactory) {
        return (clazz) -> new S3ClientImpl(httpClient, config, telemetryFactory, httpClientTelemetryFactory, clazz);
    }

    default S3Config s3Config(Config config, ConfigValueExtractor<S3Config> extractor) {
        var value = config.get("s3");
        return extractor.extract(value);
    }

    @DefaultComponent
    default S3LoggerFactory s3ClientLoggerFactory() {
        return new DefaultS3LoggerFactory();
    }

    @DefaultComponent
    default S3TelemetryFactory s3ClientTelemetryFactory(@Nullable S3LoggerFactory loggerFactory,
                                                        @Nullable S3TracerFactory tracingFactory,
                                                        @Nullable S3MetricsFactory metricsFactory) {
        return new DefaultS3TelemetryFactory(loggerFactory, tracingFactory, metricsFactory);
    }

}
