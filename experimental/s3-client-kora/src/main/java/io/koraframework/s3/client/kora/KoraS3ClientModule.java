package io.koraframework.s3.client.kora;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Tag;
import io.koraframework.config.common.ConfigValue;
import io.koraframework.config.common.exception.ConfigValueException;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.http.client.common.HttpClient;
import io.koraframework.s3.client.kora.impl.KoraS3Client;
import io.koraframework.s3.client.kora.telemetry.S3ClientTelemetryFactory;
import io.koraframework.s3.client.kora.telemetry.impl.DefaultS3ClientLoggerFactory;
import io.koraframework.s3.client.kora.telemetry.impl.DefaultS3ClientMetricsFactory;
import io.koraframework.s3.client.kora.telemetry.impl.DefaultS3ClientTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

public interface KoraS3ClientModule {

    @DefaultComponent
    default S3ClientTelemetryFactory defaultKoraS3ClientTelemetryFactory(@Nullable Tracer tracer,
                                                                         @Nullable MeterRegistry meterRegistry,
                                                                         @Nullable DefaultS3ClientLoggerFactory loggerFactory,
                                                                         @Nullable DefaultS3ClientMetricsFactory metricsFactory) {
        return new DefaultS3ClientTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }

    @Tag(S3Client.class)
    @DefaultComponent
    default HttpClient defaultKoraS3httpClient(HttpClient client) {
        return client;
    }

    @DefaultComponent
    default S3ClientFactory defaultKoraS3ClientFactory(@Tag(S3Client.class) HttpClient client,
                                                       S3ClientTelemetryFactory telemetryFactory) {
        return new S3ClientFactory() {
            @Override
            public S3Client create(S3ClientConfig config) {
                return create("s3", KoraS3Client.class, config);
            }

            @Override
            public S3Client create(String configPath, Class<?> clientImpl, S3ClientConfig config) {
                var telemetry = telemetryFactory.get(configPath, clientImpl, config.telemetry());
                return new KoraS3Client(client, config, telemetry);
            }
        };
    }

    @DefaultComponent
    default ConfigValueMapper<S3Credentials> koraS3CredentialsValueExtractor() {
        return src -> {
            if (src instanceof ConfigValue.NullValue) {
                return null;
            }
            var configObject = src.asObject();
            var accessKey = configObject.get("accessKey");
            if (accessKey.isNull()) {
                throw ConfigValueException.missingValue(accessKey);
            }
            var secretKey = configObject.get("secretKey");
            if (secretKey.isNull()) {
                throw ConfigValueException.missingValue(secretKey);
            }
            return S3Credentials.of(accessKey.asString(), secretKey.asString());
        };
    }
}
