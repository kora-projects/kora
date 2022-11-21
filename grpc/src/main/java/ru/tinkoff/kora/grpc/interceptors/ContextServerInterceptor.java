package ru.tinkoff.kora.grpc.interceptors;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import ru.tinkoff.kora.common.Context;

public class ContextServerInterceptor implements ServerInterceptor {
    @Override
     public  <ReqT, RespT>  ServerCall.Listener<ReqT> interceptCall(
        ServerCall<ReqT, RespT> call,
        Metadata headers,
        ServerCallHandler<ReqT, RespT> next
    )  {
        var context = Context.current();
        return new ContextualServerCallListener<>(next.startCall(call, headers), context);
    }
}
