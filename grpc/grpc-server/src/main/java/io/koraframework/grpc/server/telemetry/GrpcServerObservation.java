package io.koraframework.grpc.server.telemetry;

import io.grpc.Metadata;
import io.grpc.Status;
import io.koraframework.common.telemetry.Observation;

public interface GrpcServerObservation extends Observation {
    void observeHeaders(Metadata headers);

    void observeRequest(int numMessages);

    void observeSendMessage(Object request);

    void observeClose(Status status, Metadata trailers);

    void observeCancel();

    void observeComplete();

    void observeHalfClosed();

    void observeReceiveMessage(Object response);

    void observeReady();

    void observeStart();
}
