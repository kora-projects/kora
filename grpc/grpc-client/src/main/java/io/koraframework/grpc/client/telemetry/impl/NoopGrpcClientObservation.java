package io.koraframework.grpc.client.telemetry.impl;

import io.grpc.Metadata;
import io.grpc.Status;
import io.koraframework.grpc.client.telemetry.GrpcClientObservation;
import io.opentelemetry.api.trace.Span;

public final class NoopGrpcClientObservation implements GrpcClientObservation {

    public static final NoopGrpcClientObservation INSTANCE = new NoopGrpcClientObservation();

    private NoopGrpcClientObservation() {}

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
