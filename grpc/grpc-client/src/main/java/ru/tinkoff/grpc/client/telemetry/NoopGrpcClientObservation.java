package ru.tinkoff.grpc.client.telemetry;

import io.grpc.Metadata;
import io.grpc.Status;
import io.opentelemetry.api.trace.Span;

public class NoopGrpcClientObservation implements GrpcClientObservation {
    public static final NoopGrpcClientObservation INSTANCE = new NoopGrpcClientObservation();

    @Override
    public void observeStart(Metadata headers) {

    }

    @Override
    public void observeSend(Object message) {

    }

    @Override
    public void observeReceive(Object message) {

    }

    @Override
    public void observeClose(Status status, Metadata trailers) {

    }

    @Override
    public Span span() {
        return Span.getInvalid();
    }

    @Override
    public void end() {

    }

    @Override
    public void observeError(Throwable e) {

    }
}
