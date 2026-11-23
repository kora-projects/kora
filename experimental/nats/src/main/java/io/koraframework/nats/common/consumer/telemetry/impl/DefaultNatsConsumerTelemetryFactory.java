package io.koraframework.nats.common.consumer.telemetry.impl;

import io.koraframework.micrometer.common.NoopMeterRegistry;
import io.koraframework.nats.common.consumer.telemetry.NatsConsumerTelemetry;
import io.koraframework.nats.common.consumer.telemetry.NatsConsumerTelemetryConfig;
import io.koraframework.nats.common.consumer.telemetry.NatsConsumerTelemetryFactory;
import io.koraframework.nats.common.consumer.telemetry.impl.DefaultNatsConsumerTelemetry.TelemetryContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

import java.util.Properties;

public class DefaultNatsConsumerTelemetryFactory implements NatsConsumerTelemetryFactory {
    private final @Nullable Tracer tracer;
    private final @Nullable MeterRegistry registry;
    private final @Nullable DefaultNatsConsumerLoggerFactory loggerFactory;
    private final @Nullable DefaultNatsConsumerMetricsFactory metricsFactory;

    public DefaultNatsConsumerTelemetryFactory(@Nullable Tracer tracer, @Nullable MeterRegistry registry,
                                               @Nullable DefaultNatsConsumerLoggerFactory loggerFactory,
                                               @Nullable DefaultNatsConsumerMetricsFactory metricsFactory) {
        this.tracer = tracer;
        this.registry = registry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public NatsConsumerTelemetry get(String configPath, String canonicalName, NatsConsumerTelemetryConfig config, Properties driverProperties) {
        var tracing = tracer != null && config.tracing().enabled();
        var metrics = registry != null && config.metrics().enabled();
        if (!tracing && !metrics && !config.logging().enabled()) {
            return NoopNatsConsumerTelemetry.INSTANCE;
        }
        var context = new TelemetryContext(configPath, canonicalName, config,
            tracing ? tracer : TracerProvider.noop().get("nats-consumer"), metrics ? registry : NoopMeterRegistry.INSTANCE,
            tracing, metrics);
        return build(context,
            loggerFactory == null ? DefaultNatsConsumerLoggerFactory.INSTANCE : loggerFactory,
            metricsFactory == null ? DefaultNatsConsumerMetricsFactory.INSTANCE : metricsFactory);
    }

    protected NatsConsumerTelemetry build(TelemetryContext context, DefaultNatsConsumerLoggerFactory loggerFactory,
                                          DefaultNatsConsumerMetricsFactory metricsFactory) {
        return new DefaultNatsConsumerTelemetry(context, loggerFactory.create(context), metricsFactory.create(context));
    }
}
