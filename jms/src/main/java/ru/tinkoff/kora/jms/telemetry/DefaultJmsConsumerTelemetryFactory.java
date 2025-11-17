package ru.tinkoff.kora.jms.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import jakarta.annotation.Nullable;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public final class DefaultJmsConsumerTelemetryFactory implements JmsConsumerTelemetryFactory {

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;

    public DefaultJmsConsumerTelemetryFactory(@Nullable Tracer tracer, @Nullable MeterRegistry meterRegistry) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public JmsConsumerTelemetry get(TelemetryConfig config, String queueName) {
        var logger = config.logging().enabled()
            ? LoggerFactory.getLogger("ru.tinkoff.kora.jms." + queueName)
            : NOPLogger.NOP_LOGGER;
        var tracer = this.tracer == null || !config.tracing().enabled()
            ? TracerProvider.noop().get("jms-telemetry")
            : this.tracer;
        var meterRegistry = this.meterRegistry == null || !config.metrics().enabled()
            ? new CompositeMeterRegistry()
            : this.meterRegistry;
        return new DefaultJmsConsumerTelemetry(
            config, tracer, meterRegistry, logger
        );
    }
}
