package io.koraframework.kafka.common.consumer.telemetry;

import io.koraframework.micrometer.api.MeterBuilder;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.ErrorAttributes;

import java.util.concurrent.TimeUnit;

public class DefaultKafkaConsumerRecordObservation implements KafkaConsumerRecordObservation {

    protected final Span span;
    protected final MeterBuilder<Timer> durationBuilder;

    private Throwable error;
    private long handle;

    public DefaultKafkaConsumerRecordObservation(Span span, MeterBuilder<Timer> durationBuilder) {
        this.span = span;
        this.durationBuilder = durationBuilder;
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
        var took = System.nanoTime() - handle;

        var errorValue = error == null ? "" : error.getClass().getCanonicalName();
        var timer = this.durationBuilder.tag(ErrorAttributes.ERROR_TYPE.getKey(), errorValue).build();
        timer.record(took, TimeUnit.NANOSECONDS);

        if (this.error == null) {
            this.span.setStatus(StatusCode.OK);
        }
        this.span.setAttribute(ErrorAttributes.ERROR_TYPE.getKey(), errorValue);
        this.span.end();
    }

    @Override
    public void observeError(Throwable e) {
        this.error = e;
        this.span.setStatus(StatusCode.ERROR);
        this.span.recordException(e);
    }
}
