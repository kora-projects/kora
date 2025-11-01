package ru.tinkoff.grpc.client.telemetry;

import io.grpc.Metadata;
import io.grpc.Status;
import ru.tinkoff.kora.common.telemetry.Observation;

public interface GrpcClientObservation extends Observation {
    void observeStart(Metadata headers);

    void observeSend(Object message);

    void observeReceive(Object message);

    void observeClose(Status status, Metadata trailers);

}
