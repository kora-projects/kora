package io.koraframework.grpc.server.app;

import io.grpc.stub.StreamObserver;
import io.koraframework.grpc.server.events.EventsGrpc;
import io.koraframework.grpc.server.events.SendEventRequest;
import io.koraframework.grpc.server.events.SendEventResponse;

public final class EventService extends EventsGrpc.EventsImplBase {
    private final String res;

    public EventService(String res) {
        this.res = res;
    }

    @Override
    public void sendEvent(SendEventRequest request, StreamObserver<SendEventResponse> responseObserver) {
        responseObserver.onNext(SendEventResponse.newBuilder().setRes(res).build());
        responseObserver.onCompleted();
    }
}
