package ru.tinkoff.kora.kafka.common.consumer.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

import java.util.Properties;

public final class DefaultKafkaConsumerTelemetryFactory implements KafkaConsumerTelemetryFactory {
    private final Tracer tracer;
    private final MeterRegistry meterRegistry;

    public DefaultKafkaConsumerTelemetryFactory(@Nullable Tracer tracer, @Nullable MeterRegistry meterRegistry) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public KafkaConsumerTelemetry get(String consumerName, Properties driverProperties, KafkaConsumerTelemetryConfig config) {
        if (!config.tracing().enabled() && !config.metrics().enabled() && !config.logging().enabled()) {
            return new NoopKafkaConsumerTelemetry();
        }
        var tracer = config.tracing().enabled() ? this.tracer : TracerProvider.noop().get("kafka-consumer");
        var meterRegistry = config.metrics().enabled() ? this.meterRegistry : new CompositeMeterRegistry();

        return new DefaultKafkaConsumerTelemetry(config, tracer, meterRegistry, consumerName, driverProperties);
    }
}
