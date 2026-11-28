package io.koraframework.grpc.server.handler;

import io.grpc.*;
import io.koraframework.common.context.*;
import io.koraframework.common.telemetry.*;
import io.koraframework.logging.common.*;
import io.opentelemetry.context.Context;

import java.util.concurrent.*;

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
    public void transportTerminated(Attributes transportAttrs) {
        transportAttrs.get(EXECUTOR_KEY).shutdownNow();
    }

    @Override
    public <ReqT, RespT> Executor getExecutor(ServerCall<ReqT, RespT> call, Metadata metadata) {
        var mdc = new MDC();
        var context = Context.root();
        var executor = call.getAttributes().get(EXECUTOR_KEY);
        return command -> executor.execute(new GrpcServerTaskRunner(mdc, context, command));
    }

    private static final class GrpcServerTaskRunner implements Runnable {
        private final MDC mdc;
        private final Context context;
        private final Runnable command;

        public GrpcServerTaskRunner(MDC mdc, Context context, Runnable command) {
            this.mdc = mdc;
            this.context = context;
            this.command = command;
        }

        @Override
        public void run() {
            var koraContext = KoraContext.empty()
                .with(MDC.MDC_KEY, mdc)
                .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, context);

            KoraContext.runWith(koraContext, command);
        }
    }
}
