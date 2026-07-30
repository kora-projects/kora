package io.koraframework.http.server.common.system;

import io.koraframework.application.graph.ValueOf;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.body.HttpBodyOutput;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.http.server.common.request.HttpServerRequestHandler;
import io.koraframework.telemetry.common.MetricsScraper;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class MetricsHandler implements HttpServerRequestHandler {

    private final SystemHttpServerConfig config;
    private final ValueOf<Optional<MetricsScraper>> meterRegistry;

    public MetricsHandler(SystemHttpServerConfig config, ValueOf<Optional<MetricsScraper>> meterRegistry) {
        this.config = config;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public String method() {
        return "GET";
    }

    @Override
    public String routeTemplate() {
        return this.config.metricsPath();
    }

    @Override
    public HttpServerResponse handle(HttpServerRequest request) throws Exception {
        var registry = this.meterRegistry.get().orElse(null);
        if (registry == null) {
            return HttpServerResponse.of(200, HttpBody.plaintext("# Metric Scraper disabled"));
        }

        return HttpServerResponse.of(200, HttpBodyOutput.of("text/plain", registry::scrape));
    }
}
