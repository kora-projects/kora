package ru.tinkoff.kora.opentelemetry.module.grpc.server;

import io.grpc.Status;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import ru.tinkoff.kora.grpc.telemetry.GrpcServerTracer;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicLong;

public final class OpentelemetryGrpcServerSpan implements GrpcServerTracer.GrpcServerSpan {
    private final Span span;
    private final AtomicLong sentCounter = new AtomicLong(0);
    private final AtomicLong receivedCounter = new AtomicLong(0);

    public OpentelemetryGrpcServerSpan(Span span) {
        this.span = span;
    }

    @Override
    public void close(Status status, @Nullable Throwable exception) {
        if (exception != null) {
            this.span.setStatus(StatusCode.ERROR);
        }
        this.span.end();
    }

    @Override
    public void addSend(Object message) {
        this.span.addEvent(
            "message",
            Attributes.of(
                SemanticAttributes.MESSAGE_TYPE, "SENT",
                SemanticAttributes.MESSAGE_ID, sentCounter.incrementAndGet()
            )
        );
    }

    @Override
    public void addReceive(Object message) {
        this.span.addEvent(
            "message",
            Attributes.of(
                SemanticAttributes.MESSAGE_TYPE, "RECEIVED",
                SemanticAttributes.MESSAGE_ID, receivedCounter.incrementAndGet()
            )
        );
    }
}
