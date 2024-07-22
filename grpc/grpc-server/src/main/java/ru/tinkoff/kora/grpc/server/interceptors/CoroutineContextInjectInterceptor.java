package ru.tinkoff.kora.grpc.server.interceptors;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.kotlin.CoroutineContextServerInterceptor;
import jakarta.annotation.Nonnull;
import kotlin.coroutines.CoroutineContext;
import ru.tinkoff.kora.common.Context;

public class CoroutineContextInjectInterceptor {
    public static ServerInterceptor newInstance() {
        try {
            CoroutineContextInjectInterceptor.class.getClassLoader().loadClass("kotlinx.coroutines.Dispatchers");
            return new CoroutineContextInjectInterceptorDelegate();
        } catch (ClassNotFoundException e) {
            return new ServerInterceptor() {
                @Override
                public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
                    return next.startCall(call, headers);
                }
            };
        }
    }

    public static class CoroutineContextInjectInterceptorDelegate extends CoroutineContextServerInterceptor {
        @Nonnull
        @Override
        public CoroutineContext coroutineContext(@Nonnull ServerCall<?, ?> serverCall, @Nonnull Metadata metadata) {
            return Context.Kotlin.asCoroutineContext(Context.current());
        }
    }


}
