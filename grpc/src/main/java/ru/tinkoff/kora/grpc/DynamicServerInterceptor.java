package ru.tinkoff.kora.grpc;


import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import ru.tinkoff.kora.application.graph.RefreshListener;
import ru.tinkoff.kora.application.graph.ValueOf;

public final class DynamicServerInterceptor implements ServerInterceptor, RefreshListener {
    private volatile ServerInterceptor interceptor;
    private final ValueOf<ServerInterceptor> interceptorNode;

    public DynamicServerInterceptor(ValueOf<ServerInterceptor> interceptor) {
        this.interceptorNode = interceptor;
        this.interceptor = interceptor.get();
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        return interceptor.interceptCall(call, headers, next);
    }

    @Override
    public void graphRefreshed() {
        this.interceptor = interceptorNode.get();
    }

}


