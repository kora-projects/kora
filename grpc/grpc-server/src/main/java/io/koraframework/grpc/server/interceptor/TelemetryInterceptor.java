package io.koraframework.grpc.server.interceptor;

import io.grpc.*;
import io.koraframework.common.context.*;
import io.koraframework.common.telemetry.*;
import io.koraframework.grpc.server.telemetry.*;
import io.opentelemetry.context.Context;

import java.util.function.*;

public class TelemetryInterceptor implements ServerInterceptor {

    private final GrpcServerTelemetry telemetry;

    public TelemetryInterceptor(GrpcServerTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        var observation = this.telemetry.observe(call, headers);
        var context = Context.root().with(observation.span());

        KoraContext current = KoraContext.current();
        KoraContext baseContext = current != null ? current : KoraContext.empty();

        KoraContext updatedContext = Observation.attach(baseContext, observation)
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, context);

        return KoraContext.runWith(updatedContext, new InterceptCallRunner<>(next, call, headers, context, observation));
    }

    private static final class InterceptCallRunner<ReqT, RespT> implements Supplier<ServerCall.Listener<ReqT>> {
        private final ServerCallHandler<ReqT, RespT> next;
        private final ServerCall<ReqT, RespT> call;
        private final Metadata headers;
        private final Context context;
        private final GrpcServerObservation observation;

        public InterceptCallRunner(ServerCallHandler<ReqT, RespT> next,
                                   ServerCall<ReqT, RespT> call,
                                   Metadata headers,
                                   Context context,
                                   GrpcServerObservation observation) {
            this.next = next;
            this.call = call;
            this.headers = headers;
            this.context = context;
            this.observation = observation;
        }

        @Override
        public ServerCall.Listener<ReqT> get() {
            try {
                var telemetryCall = new GrpcServerTelemetryCall<>(context, observation, call);
                observation.observeStart();
                var listener = next.startCall(telemetryCall, headers);
                return new GrpcServerTelemetryCallListener<>(context, observation, listener);
            } catch (StatusRuntimeException e) {
                observation.observeClose(e.getStatus(), e.getTrailers());
                observation.observeError(e);
                observation.end();
                throw e;
            } catch (Throwable e) {
                observation.observeError(e);
                observation.end();
                throw e;
            }
        }
    }
}
