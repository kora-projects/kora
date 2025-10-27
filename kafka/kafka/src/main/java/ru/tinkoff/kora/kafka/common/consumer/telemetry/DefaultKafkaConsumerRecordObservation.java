package ru.tinkoff.kora.kafka.common.consumer.telemetry;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.ErrorAttributes;

import java.util.concurrent.TimeUnit;

public class DefaultKafkaConsumerRecordObservation implements KafkaConsumerRecordObservation {
    private final Span span;
    private final Meter.MeterProvider<Timer> duration;
    private Throwable error;
    private long handle;

    public DefaultKafkaConsumerRecordObservation(Span span, Meter.MeterProvider<Timer> duration) {
        this.span = span;
        this.duration = duration;
    }

    @Override
    public void observeHandle() {
        this.handle = System.nanoTime();
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void end() {
        if (this.error == null) {
            this.span.setStatus(StatusCode.OK);
        }
        this.span.end();
        this.duration.withTag(ErrorAttributes.ERROR_TYPE.getKey(), error == null ? "" : error.getClass().getCanonicalName())
            .record(System.nanoTime() - handle, TimeUnit.NANOSECONDS);
    }

    @Override
    public void observeError(Throwable e) {
        this.error = e;
        this.span.setStatus(StatusCode.ERROR);
        this.span.recordException(e);
    }

}
