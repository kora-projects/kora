package ru.tinkoff.grpc.client.config;

import io.grpc.*;

import java.util.concurrent.TimeUnit;

public final class GrpcClientConfigInterceptor implements ClientInterceptor {
    private final GrpcClientConfig config;

    public GrpcClientConfigInterceptor(GrpcClientConfig config) {
        this.config = config;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        if (callOptions.getDeadline() == null && this.config.timeout() != null) {
            callOptions = callOptions.withDeadlineAfter(this.config.timeout().toMillis(), TimeUnit.MILLISECONDS); // todo per method??
        }
        return next.newCall(method, callOptions);
    }
}
