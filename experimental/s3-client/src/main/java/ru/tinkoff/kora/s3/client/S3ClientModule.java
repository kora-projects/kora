package ru.tinkoff.kora.s3.client;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.config.common.ConfigValue;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractionException;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.http.client.common.HttpClient;
import ru.tinkoff.kora.s3.client.impl.S3ClientImpl;
import ru.tinkoff.kora.s3.client.telemetry.DefaultS3ClientTelemetryFactory;
import ru.tinkoff.kora.s3.client.telemetry.S3ClientTelemetryFactory;

public interface S3ClientModule {
    default S3ClientTelemetryFactory defaultS3ClientTelemetryFactory(@Nullable Tracer tracer, @Nullable MeterRegistry meterRegistry) {
        return new DefaultS3ClientTelemetryFactory(tracer, meterRegistry);
    }

    default S3ClientFactory defaultS3ClientFactory(HttpClient client, S3ClientTelemetryFactory telemetryFactory) {
        return config -> {
            var telemetry = telemetryFactory.get(config);
            return new S3ClientImpl(client, config, telemetry);
        };
    }

    default ConfigValueExtractor<AwsCredentials> awsCredentialsConfigValueExtractor() {
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
            return AwsCredentials.of(
                accessKey.asString(),
                secretKey.asString()
            );
        };
    }
}
