package ru.tinkoff.kora.grpc.server;

import io.grpc.*;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VirtualThreadExecutorTransportFilter extends ServerTransportFilter implements ServerCallExecutorSupplier {
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
    public void transportTerminated(Attributes transportAttrs) {
        transportAttrs.get(EXECUTOR_KEY).shutdownNow();
    }

    @Override
    public @Nullable <ReqT, RespT> Executor getExecutor(ServerCall<ReqT, RespT> call, Metadata metadata) {
        return call.getAttributes().get(VirtualThreadExecutorTransportFilter.EXECUTOR_KEY);
    }
}
