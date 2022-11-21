package ru.tinkoff.kora.grpc;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;

public class DynamicServerCall<Req, Res> implements  ServerCallHandler<Req, Res>  {
    private volatile ServerCallHandler<Req, Res> currentCall;

    public DynamicServerCall(ServerCallHandler<Req, Res> currentCall){
        this.currentCall = currentCall;
    }

    public void setCurrentCall(ServerCallHandler<Req, Res> currentCall) {
        this.currentCall = currentCall;
    }

    @Override
    public ServerCall.Listener<Req> startCall(ServerCall<Req, Res> call, Metadata headers) {
        return currentCall.startCall(call, headers);
    }
}
