package ru.tinkoff.kora.http.client.common.interceptor;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetry;

public class TelemetryInterceptor implements HttpClientInterceptor {

    private final HttpClientTelemetry telemetry;

    public TelemetryInterceptor(HttpClientTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    @Override
    public HttpClientResponse processRequest(Context ctx, InterceptChain chain, HttpClientRequest request) throws Exception {
        if (!this.telemetry.isEnabled()) {
            return chain.process(ctx, request);
        }
        var fork = ctx.fork();
        var telemetryContext = this.telemetry.get(fork, request);
        if (telemetryContext == null) {
            return chain.process(ctx, request);
        }
        fork.inject();
        try {
            var rs = chain.process(fork, telemetryContext.request());
            return telemetryContext.close(rs, null);
        } catch (Exception e) {
            telemetryContext.close(null, e);
            throw e;
        }
    }
}
