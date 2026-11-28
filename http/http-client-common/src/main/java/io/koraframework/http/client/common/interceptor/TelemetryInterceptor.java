package io.koraframework.http.client.common.interceptor;

import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContextStorage;
import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.client.common.telemetry.*;
import io.opentelemetry.context.Context;
import io.koraframework.common.context.KoraContext;
import java.util.function.Supplier;

public class TelemetryInterceptor implements HttpClientInterceptor {

    private final HttpClientTelemetry telemetry;

    public TelemetryInterceptor(HttpClientTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    @Override
    public HttpClientResponse processRequest(InterceptChain chain, HttpClientRequest request) throws Exception {
        var observation = this.telemetry.observe(request);
        var currentOtelCtx = Context.current();
        var updatedOtelCtx = currentOtelCtx.with(observation.span());

        KoraContext current = KoraContext.current();
        KoraContext baseContext = current != null ? current : KoraContext.empty();

        KoraContext updatedContext = Observation.attach(baseContext, observation)
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, updatedOtelCtx);

        try {
            return KoraContext.runWith(updatedContext, new HttpClientRequestRunner(chain, request, observation));
        } catch (RuntimeException e) {
            if (e.getCause() instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }

    private static final class HttpClientRequestRunner implements Supplier<HttpClientResponse> {
        private final InterceptChain chain;
        private final HttpClientRequest request;
        private final HttpClientObservation observation;

        public HttpClientRequestRunner(InterceptChain chain,
                                       HttpClientRequest request,
                                       HttpClientObservation observation) {
            this.chain = chain;
            this.request = request;
            this.observation = observation;
        }

        @Override
        public HttpClientResponse get() {
            try {
                var observedRequest = observation.observeRequest(request);
                var rs = chain.process(observedRequest);
                return observation.observeResponse(rs);
            } catch (Throwable t) {
                observation.observeError(t);
                throw new RuntimeException(t);
            } finally {
                observation.end();
            }
        }
    }
}
