package ru.tinkoff.kora.grpc.server.telemetry;

import io.grpc.Metadata;
import io.grpc.Status;
import io.opentelemetry.api.trace.Span;

public class NoopGrpcServerObservation implements GrpcServerObservation {
    public static final NoopGrpcServerObservation INSTANCE = new NoopGrpcServerObservation();

    @Override
    public void observeHeaders(Metadata headers) {

    }

    @Override
    public void observeRequest(int numMessages) {

    }

    @Override
    public void observeSendMessage(Object rs) {

    }

    @Override
    public void observeClose(Status status, Metadata trailers) {

    }

    @Override
    public void observeCancel() {

    }

    @Override
    public void observeComplete() {

    }

    @Override
    public void observeHalfClosed() {

    }

    @Override
    public void observeReceiveMessage(Object rq) {

    }

    @Override
    public void observeReady() {

    }

    @Override
    public void observeStart() {

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
