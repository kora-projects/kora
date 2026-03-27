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
    private final ValueOf<HttpServerSystemConfig> config;
    private final ValueOf<Optional<MetricsScraper>> meterRegistry;

    public MetricsHandler(ValueOf<HttpServerSystemConfig> config, ValueOf<Optional<MetricsScraper>> meterRegistry) {
        this.config = config;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public String method() {
        return "GET";
    }

    @Override
    public String routeTemplate() {
        return this.config.get().metricsPath();
    }

    @Override
    public HttpServerResponse handle(HttpServerRequest request) throws Exception {
        var registry = this.meterRegistry.get().orElse(null);
        if (registry == null) {
            return HttpServerResponse.of(200, HttpBody.plaintext(""));
        }
        return HttpServerResponse.of(200, new HttpBodyOutput() {
            @Override
            public long contentLength() {
                return -1;
            }

            @Override
            public String contentType() {
                return "text/plain";
            }

            @Override
            public void write(OutputStream os) throws IOException {
                try (var w = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                    registry.scrape(w);
                }
            }

            @Override
            public void close() throws IOException {

            }
        });
    }
}
