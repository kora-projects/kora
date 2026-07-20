package io.koraframework.grpc.server.telemetry.impl;

import io.grpc.Metadata;
import io.grpc.Status;
import io.koraframework.grpc.server.telemetry.GrpcServerObservation;
import io.opentelemetry.api.trace.Span;

public final class NoopGrpcServerObservation implements GrpcServerObservation {

    public static final GrpcServerObservation INSTANCE = new NoopGrpcServerObservation();

    private NoopGrpcServerObservation() {}

    @Override
    public void observeHeaders(Metadata headers) {

    }

    @Override
    public void observeRequest(int numMessages) {

    }

    @Override
    public void observeSendMessage(Object request) {

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
    public void observeReceiveMessage(Object response) {

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
