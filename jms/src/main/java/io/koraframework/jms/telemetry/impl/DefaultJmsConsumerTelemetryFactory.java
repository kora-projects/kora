package io.koraframework.jms.telemetry.impl;

import io.koraframework.jms.telemetry.JmsConsumerTelemetry;
import io.koraframework.jms.telemetry.JmsConsumerTelemetryConfig;
import io.koraframework.jms.telemetry.JmsConsumerTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public class DefaultJmsConsumerTelemetryFactory implements JmsConsumerTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("jms-consumer");
    public static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultJmsConsumerLoggerFactory loggerFactory;
    @Nullable
    private final DefaultJmsConsumerMetricsFactory metricsFactory;

    public DefaultJmsConsumerTelemetryFactory(@Nullable Tracer tracer,
                                              @Nullable MeterRegistry meterRegistry,
                                              @Nullable DefaultJmsConsumerLoggerFactory loggerFactory,
                                              @Nullable DefaultJmsConsumerMetricsFactory metricsFactory) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public JmsConsumerTelemetry get(JmsConsumerTelemetryConfig config, String queueName) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricsEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricsEnabled && !config.logging().enabled()) {
            return NoopJmsConsumerTelemetry.INSTANCE;
        }

        var loggerFactory = config.logging().enabled()
            ? (this.loggerFactory != null ? this.loggerFactory : DefaultJmsConsumerLoggerFactory.INSTANCE)
            : NoopJmsConsumerLoggerFactory.INSTANCE;
        var metricsFactory = metricsEnabled
            ? (this.metricsFactory != null ? this.metricsFactory : DefaultJmsConsumerMetricsFactory.INSTANCE)
            : NoopJmsConsumerMetricsFactory.INSTANCE;

        return build(
            queueName,
            config,
            traceEnabled ? this.tracer : NOOP_TRACER,
            metricsEnabled ? this.meterRegistry : NOOP_METER_REGISTRY,
            metricsFactory,
            loggerFactory
        );
    }

    protected JmsConsumerTelemetry build(String queueName,
                                         JmsConsumerTelemetryConfig config,
                                         Tracer tracer,
                                         MeterRegistry meterRegistry,
                                         DefaultJmsConsumerMetricsFactory metricsFactory,
                                         DefaultJmsConsumerLoggerFactory loggerFactory) {
        return new DefaultJmsConsumerTelemetry(queueName, config, tracer, meterRegistry, metricsFactory, loggerFactory);
    }
}
