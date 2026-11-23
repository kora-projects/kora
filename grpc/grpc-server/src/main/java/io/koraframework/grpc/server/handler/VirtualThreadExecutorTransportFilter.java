package io.koraframework.grpc.server.handler;

import io.grpc.*;
import io.opentelemetry.context.Context;
import io.koraframework.common.telemetry.OpentelemetryContext;
import io.koraframework.logging.common.MDC;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class VirtualThreadExecutorTransportFilter extends ServerTransportFilter implements ServerCallExecutorSupplier {

    public static final Attributes.Key<ExecutorService> EXECUTOR_KEY = Attributes.Key.create("virtual-thread-executor");

    public static final VirtualThreadExecutorTransportFilter INSTANCE = new VirtualThreadExecutorTransportFilter();

    @Override
    public Attributes transportReady(Attributes transportAttrs) {
        return transportAttrs.toBuilder()
            .set(EXECUTOR_KEY, Executors.newSingleThreadExecutor(command -> {
                var addr = "" + transportAttrs.get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
                if (addr.startsWith("/")) {
                    addr = addr.substring(1);
                }
                return Thread.ofVirtual()
                    .name("grpc-" + addr)
                    .unstarted(command);
            }))
            .build();
    }

    @Override
    public void transportTerminated(@Nullable Attributes transportAttrs) {
        // grpc passes null when the transport terminates before transportReady, e.g. a TCP probe without the HTTP/2 preface
        var executor = transportAttrs == null ? null : transportAttrs.get(EXECUTOR_KEY);
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Override
    public <ReqT, RespT> Executor getExecutor(ServerCall<ReqT, RespT> call, Metadata metadata) {
        var mdc = new MDC();
        var context = Context.root();
        return command -> {
            var executor = call.getAttributes().get(VirtualThreadExecutorTransportFilter.EXECUTOR_KEY);
            executor.execute(() -> ScopedValue.where(MDC.VALUE, mdc)
                .where(OpentelemetryContext.VALUE, context)
                .run(command));
        };
    }
}
