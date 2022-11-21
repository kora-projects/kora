package ru.tinkoff.kora.grpc.interceptors;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.kotlin.CoroutineContextServerInterceptor;
import kotlin.coroutines.CoroutineContext;
import ru.tinkoff.kora.common.Context;

import javax.annotation.Nonnull;

public class CoroutineContextInjectInterceptor {

    public static io.grpc.ServerInterceptor newInstance() {
        try {
            CoroutineContextInjectInterceptor.class.getClassLoader().loadClass("kotlinx.coroutines.Dispatchers");
            CoroutineContextInjectInterceptor.class.getClassLoader().loadClass("kotlinx.coroutines.reactor.ReactorContextKt");
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
        private final CoroutineContext rootContext;

        public CoroutineContextInjectInterceptorDelegate() {
            this.rootContext = (CoroutineContext) kotlinx.coroutines.Dispatchers.getUnconfined();
        }

        @Nonnull
        @Override
        public CoroutineContext coroutineContext(@Nonnull ServerCall<?, ?> serverCall, @Nonnull Metadata metadata) {
            return Context.Kotlin.inject(this.rootContext, Context.current());
        }
    }


}
