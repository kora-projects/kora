package io.koraframework.nats.common.producer.telemetry.impl;

import io.koraframework.micrometer.common.NoopMeterRegistry;
import io.koraframework.nats.common.producer.telemetry.NatsPublisherTelemetry;
import io.koraframework.nats.common.producer.telemetry.NatsPublisherTelemetryConfig;
import io.koraframework.nats.common.producer.telemetry.NatsPublisherTelemetryFactory;
import io.koraframework.nats.common.producer.telemetry.impl.DefaultNatsPublisherTelemetry.TelemetryContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

import java.util.Properties;

public class DefaultNatsPublisherTelemetryFactory implements NatsPublisherTelemetryFactory {
    private final @Nullable Tracer tracer;
    private final @Nullable MeterRegistry registry;
    private final @Nullable DefaultNatsPublisherLoggerFactory loggerFactory;
    private final @Nullable DefaultNatsPublisherMetricsFactory metricsFactory;

    public DefaultNatsPublisherTelemetryFactory(@Nullable Tracer tracer, @Nullable MeterRegistry registry,
                                                @Nullable DefaultNatsPublisherLoggerFactory loggerFactory,
                                                @Nullable DefaultNatsPublisherMetricsFactory metricsFactory) {
        this.tracer = tracer;
        this.registry = registry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public NatsPublisherTelemetry get(String configPath, String canonicalName, NatsPublisherTelemetryConfig config, Properties driverProperties) {
        var tracing = tracer != null && config.tracing().enabled();
        var metrics = registry != null && config.metrics().enabled();
        if (!tracing && !metrics && !config.logging().enabled()) {
            return NoopNatsPublisherTelemetry.INSTANCE;
        }
        var context = new TelemetryContext(configPath, canonicalName, config,
            tracing ? tracer : TracerProvider.noop().get("nats-publisher"), metrics ? registry : NoopMeterRegistry.INSTANCE,
            tracing, metrics);
        return build(context,
            loggerFactory == null ? DefaultNatsPublisherLoggerFactory.INSTANCE : loggerFactory,
            metricsFactory == null ? DefaultNatsPublisherMetricsFactory.INSTANCE : metricsFactory);
    }

    protected NatsPublisherTelemetry build(TelemetryContext context, DefaultNatsPublisherLoggerFactory loggerFactory,
                                           DefaultNatsPublisherMetricsFactory metricsFactory) {
        return new DefaultNatsPublisherTelemetry(context, loggerFactory.create(context), metricsFactory.create(context));
    }
}
