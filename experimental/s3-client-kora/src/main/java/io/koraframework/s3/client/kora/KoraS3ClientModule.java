package io.koraframework.s3.client.kora;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.FactoryModule;
import io.koraframework.config.common.ConfigValue;
import io.koraframework.config.common.exception.ConfigValueException;
import io.koraframework.config.common.mapper.ConfigValueMapper;
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

    @FactoryModule
    default S3FactoryModule defaultKoraS3Factory() {
        return new S3FactoryModule("s3");
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
