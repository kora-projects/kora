package io.koraframework.redis.jedis.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public final class DefaultJedisTelemetryFactory implements JedisTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("jedis");
    public static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultJedisMetricsFactory metricsFactory;

    public DefaultJedisTelemetryFactory(@Nullable Tracer tracer,
                                        @Nullable MeterRegistry meterRegistry,
                                        @Nullable DefaultJedisMetricsFactory metricsFactory) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public JedisTelemetry get(JedisTelemetryConfig config) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopJedisTelemetry.INSTANCE;
        }

        var tracer = traceEnabled ? this.tracer : NOOP_TRACER;
        var meterRegistry = metricEnabled ? this.meterRegistry : NOOP_METER_REGISTRY;
        DefaultJedisMetricsFactory enabledMetricsFactory;
        if (metricEnabled) {
            enabledMetricsFactory = this.metricsFactory != null
                ? this.metricsFactory
                : DefaultJedisMetricsFactory.INSTANCE;
        } else {
            enabledMetricsFactory = NoopJedisMetricsFactory.INSTANCE;
        }

        return new DefaultJedisTelemetry(config, tracer, meterRegistry, enabledMetricsFactory);
    }
}
