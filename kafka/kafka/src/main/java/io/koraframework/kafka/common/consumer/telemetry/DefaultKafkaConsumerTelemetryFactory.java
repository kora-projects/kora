package io.koraframework.kafka.common.consumer.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

import java.util.Properties;

public final class DefaultKafkaConsumerTelemetryFactory implements KafkaConsumerTelemetryFactory {

    private static final Tracer NOOP_TRACER = TracerProvider.noop().get("kafka-listener");
    private static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;

    public DefaultKafkaConsumerTelemetryFactory(@Nullable Tracer tracer,
                                                @Nullable MeterRegistry meterRegistry) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public KafkaConsumerTelemetry get(String listenerName, String listenerImpl, Properties driverProperties, KafkaConsumerTelemetryConfig config) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopKafkaConsumerTelemetry.INSTANCE;
        }

        var tracer = traceEnabled ? this.tracer : NOOP_TRACER;
        var meterRegistry = metricEnabled ? this.meterRegistry : NOOP_METER_REGISTRY;
        return new DefaultKafkaConsumerTelemetry(config, tracer, meterRegistry, listenerName, listenerImpl, driverProperties);
    }
}
