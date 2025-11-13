package ru.tinkoff.kora.scheduling.common.telemetry;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.ErrorAttributes;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

import java.util.concurrent.TimeUnit;

public class DefaultSchedulingObservation implements SchedulingObservation {
    private final long start = System.nanoTime();
    private final Class<?> jobClass;
    private final String jobMethod;
    private final Span span;
    private final Meter.MeterProvider<Timer> duration;
    private final Logger logger;
    @Nullable
    private Throwable error;

    public DefaultSchedulingObservation(Class<?> jobClass, String jobMethod, Span span, Meter.MeterProvider<Timer> duration, Logger logger) {
        this.jobClass = jobClass;
        this.jobMethod = jobMethod;
        this.span = span;
        this.duration = duration;
        this.logger = logger;
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void observeRun() {
        this.logger
            .atDebug()
            .addKeyValue("scheduledJob", StructuredArgument.value(gen -> {
                gen.writeStartObject();
                gen.writeStringField("jobClass", this.jobClass.getCanonicalName());
                gen.writeStringField("jobMethod", this.jobMethod);
                gen.writeEndObject();
            }))
            .log("Scheduled Job execution started...");

    }

    @Override
    public void observeError(Throwable e) {
        this.error = e;
        this.span.recordException(e);
        this.span.setStatus(StatusCode.ERROR);
    }

    @Override
    public void end() {
        var durationInNanos = System.nanoTime() - start;

        recordDuration(durationInNanos);
        writeLog(durationInNanos);
        closeSpan();
    }

    protected void closeSpan() {
        if (this.error == null) {
            this.span.setStatus(StatusCode.OK);
        }
        this.span.end();
    }

    protected void writeLog(long durationInNanos) {
        logger
            .atLevel(error == null ? Level.INFO : Level.WARN)
            .addKeyValue("scheduledJob", StructuredArgument.value(gen -> {
                gen.writeStartObject();
                gen.writeStringField("jobClass", this.jobClass.getCanonicalName());
                gen.writeStringField("jobMethod", this.jobMethod);
                long durationMs = durationInNanos / 1_000_000;
                gen.writeNumberField("duration", durationMs);
                gen.writeEndObject();
            }))
            .setCause(error)
            .log(error == null ? "Scheduled Job execution completed" : "Scheduled Job execution failed with error");
    }

    protected void recordDuration(long durationInNanos) {
        duration.withTag(ErrorAttributes.ERROR_TYPE.getKey(), error == null ? "" : error.getClass().getCanonicalName())
            .record(durationInNanos, TimeUnit.NANOSECONDS);
    }
}
