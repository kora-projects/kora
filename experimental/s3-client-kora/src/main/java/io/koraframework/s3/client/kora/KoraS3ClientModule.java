package io.koraframework.s3.client.kora;

import io.koraframework.common.DefaultComponent;
import io.koraframework.common.Tag;
import io.koraframework.config.common.ConfigValue;
import io.koraframework.config.common.extractor.ConfigValueExtractionException;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.http.client.common.HttpClient;
import io.koraframework.s3.client.kora.impl.KoraS3Client;
import io.koraframework.s3.client.kora.telemetry.DefaultS3ClientTelemetryFactory;
import io.koraframework.s3.client.kora.telemetry.S3ClientTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

public interface KoraS3ClientModule {

    default S3ClientTelemetryFactory defaultKoraS3ClientTelemetryFactory(@Nullable Tracer tracer,
                                                                         @Nullable MeterRegistry meterRegistry) {
        return new DefaultS3ClientTelemetryFactory(tracer, meterRegistry);
    }

    @Tag(S3Client.class)
    @DefaultComponent
    default HttpClient defaultKoraS3httpClient(HttpClient client) {
        return client;
    }

    default S3ClientFactory defaultKoraS3ClientFactory(@Tag(S3Client.class) HttpClient client,
                                                       S3ClientTelemetryFactory telemetryFactory) {
        return config -> {
            var telemetry = telemetryFactory.get(config.telemetry());
            return new KoraS3Client(client, config, telemetry);
        };
    }

    default ConfigValueExtractor<S3Credentials> koraS3CredentialsValueExtractor() {
        return src -> {
            if (src instanceof ConfigValue.NullValue) {
                return null;
            }
            var configObject = src.asObject();
            var accessKey = configObject.get("accessKey");
            if (accessKey.isNull()) {
                throw ConfigValueExtractionException.missingValue(accessKey);
            }
            var secretKey = configObject.get("secretKey");
            if (secretKey.isNull()) {
                throw ConfigValueExtractionException.missingValue(secretKey);
            }
            return S3Credentials.of(accessKey.asString(), secretKey.asString());
        };
    }
}
