package ru.tinkoff.kora.grpc.server.telemetry;

import io.grpc.Metadata;
import io.grpc.Status;
import ru.tinkoff.kora.common.telemetry.Observation;

public interface GrpcServerObservation extends Observation {
    void observeHeaders(Metadata headers);

    void observeRequest(int numMessages);

    void observeSendMessage(Object rs);

    void observeClose(Status status, Metadata trailers);

    void observeCancel();

    void observeComplete();

    void observeHalfClosed();

    void observeReceiveMessage(Object rq);

    void observeReady();

    void observeStart();
}
