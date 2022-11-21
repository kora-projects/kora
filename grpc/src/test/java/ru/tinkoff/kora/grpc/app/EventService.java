package ru.tinkoff.kora.grpc.app;

import io.grpc.stub.StreamObserver;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.grpc.EventsGrpc;
import ru.tinkoff.kora.grpc.SendEventRequest;
import ru.tinkoff.kora.grpc.SendEventResponse;

@Component
public class EventService extends EventsGrpc.EventsImplBase {
    private final String res;

    public EventService(String res) {this.res = res;}

    @Override
    public void sendEvent(SendEventRequest request, StreamObserver<SendEventResponse> responseObserver) {
        responseObserver.onNext(SendEventResponse.newBuilder().setRes(res).build());
        responseObserver.onCompleted();
    }
}
