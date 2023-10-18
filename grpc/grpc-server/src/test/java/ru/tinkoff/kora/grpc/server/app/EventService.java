package ru.tinkoff.kora.grpc.server.app;

import io.grpc.stub.StreamObserver;
import ru.tinkoff.kora.grpc.server.events.EventsGrpc;
import ru.tinkoff.kora.grpc.server.events.SendEventRequest;
import ru.tinkoff.kora.grpc.server.events.SendEventResponse;

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
