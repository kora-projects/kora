package io.koraframework.s3.client.aws;

import io.koraframework.common.annotation.FactoryModule;
import io.koraframework.s3.client.aws.telemetry.AwsS3ClientTelemetryFactory;
import io.koraframework.s3.client.aws.telemetry.impl.DefaultAwsS3ClientLoggerFactory;
import io.koraframework.s3.client.aws.telemetry.impl.DefaultAwsS3ClientMetricsFactory;
import io.koraframework.s3.client.aws.telemetry.impl.DefaultAwsS3ClientTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

public interface AwsS3ClientModule {

    @FactoryModule
    default AwsS3ClientFactoryModule awsS3ClientFactoryModule() {
        return new AwsS3ClientFactoryModule("s3client.aws");
    }

    default AwsS3ClientTelemetryFactory awsS3ClientTelemetryFactory(@Nullable Tracer tracer,
                                                                    @Nullable MeterRegistry meterRegistry,
                                                                    @Nullable DefaultAwsS3ClientLoggerFactory loggerFactory,
                                                                    @Nullable DefaultAwsS3ClientMetricsFactory metricsFactory) {
        return new DefaultAwsS3ClientTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }
}
