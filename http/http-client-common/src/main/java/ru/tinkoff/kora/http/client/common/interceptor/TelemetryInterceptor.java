package ru.tinkoff.kora.http.client.common.interceptor;

import io.opentelemetry.context.Context;
import ru.tinkoff.kora.common.telemetry.Observation;
import ru.tinkoff.kora.common.telemetry.OpentelemetryContext;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetry;

public class TelemetryInterceptor implements HttpClientInterceptor {

    private final HttpClientTelemetry telemetry;

    public TelemetryInterceptor(HttpClientTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    @Override
    public HttpClientResponse processRequest(InterceptChain chain, HttpClientRequest request) throws Exception {
        var observation = this.telemetry.observe(request);
        return ScopedValue.where(OpentelemetryContext.VALUE, Context.current().with(observation.span()))
            .where(Observation.VALUE, observation)
            .call(() -> {
                try {
                    var observedRequest = observation.observeRequest(request);
                    var rs = chain.process(observedRequest);
                    return observation.observeResponse(rs);
                } catch (Throwable t) {
                    observation.observeError(t);
                    throw t;
                } finally {
                    observation.end();
                }
            });
    }
}
