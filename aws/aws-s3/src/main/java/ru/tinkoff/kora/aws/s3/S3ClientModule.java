package ru.tinkoff.kora.aws.s3;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.aws.s3.impl.S3ClientImpl;
import ru.tinkoff.kora.aws.s3.telemetry.DefaultS3ClientTelemetryFactory;
import ru.tinkoff.kora.aws.s3.telemetry.S3ClientTelemetryFactory;
import ru.tinkoff.kora.config.common.ConfigValue;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractionException;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.http.client.common.HttpClient;

public interface S3ClientModule {
    default S3ClientTelemetryFactory defaultS3ClientTelemetryFactory(@Nullable Tracer tracer, @Nullable MeterRegistry meterRegistry) {
        return new DefaultS3ClientTelemetryFactory(tracer, meterRegistry);
    }

    default S3ClientFactory defaultS3ClientFactory(HttpClient client) {
        return config -> new S3ClientImpl(client, config);
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
