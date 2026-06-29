package io.koraframework.jms;

import io.koraframework.jms.telemetry.JmsConsumerTelemetryFactory;
import io.koraframework.jms.telemetry.impl.DefaultJmsConsumerLoggerFactory;
import io.koraframework.jms.telemetry.impl.DefaultJmsConsumerMetricsFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;
import io.koraframework.common.DefaultComponent;
import io.koraframework.jms.telemetry.impl.DefaultJmsConsumerTelemetryFactory;

public interface JmsConsumerModule {

    @DefaultComponent
    default JmsConsumerTelemetryFactory defaultJmsConsumerTelemetryFactory(@Nullable Tracer tracer,
                                                                          @Nullable MeterRegistry meterRegistry,
                                                                          @Nullable DefaultJmsConsumerLoggerFactory loggerFactory,
                                                                          @Nullable DefaultJmsConsumerMetricsFactory metricsFactory) {
        return new DefaultJmsConsumerTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }
}
