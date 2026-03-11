package io.koraframework.grpc.client.telemetry;

import io.grpc.Metadata;
import io.grpc.Status;
import io.koraframework.common.telemetry.Observation;

public interface GrpcClientObservation extends Observation {
    void observeStart(Metadata headers);

    void observeSend(Object message);

    void observeReceive(Object message);

    void observeClose(Status status, Metadata trailers);

}
