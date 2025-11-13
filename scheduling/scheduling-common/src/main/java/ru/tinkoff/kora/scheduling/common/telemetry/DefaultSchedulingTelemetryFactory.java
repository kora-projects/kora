package ru.tinkoff.kora.scheduling.common.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import jakarta.annotation.Nullable;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public final class DefaultSchedulingTelemetryFactory implements SchedulingTelemetryFactory {
    private final TelemetryConfig config;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final Tracer tracer;

    public DefaultSchedulingTelemetryFactory(TelemetryConfig config, @Nullable MeterRegistry meterRegistry, @Nullable Tracer tracer) {
        this.config = config;
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
    }

    @Override
    public SchedulingTelemetry get(@Nullable JobTelemetryConfig jobTelemetryConfig, Class<?> jobClass, String jobMethod) {
        var config = new SchedulingTelemetryConfig(this.config, jobTelemetryConfig);
        var meterRegistry = this.meterRegistry;
        if (!config.metrics().enabled() || meterRegistry == null) {
            meterRegistry = new CompositeMeterRegistry();
        }
        var tracer = this.tracer;
        if (!config.tracing().enabled() || tracer == null) {
            tracer = TracerProvider.noop().get("scheduling-telemetry");
        }
        var logger = config.logging().enabled()
            ? LoggerFactory.getLogger(jobClass.getCanonicalName() + "." + jobMethod)
            : NOPLogger.NOP_LOGGER;
        return new DefaultSchedulingTelemetry(jobClass, jobMethod, config, meterRegistry, tracer, logger);
    }
}
