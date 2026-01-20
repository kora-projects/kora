package ru.tinkoff.kora.http.server.common.privateapi;

import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.PrivateHttpServerConfig;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;
import ru.tinkoff.kora.telemetry.common.MetricsScraper;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class MetricsHandler implements HttpServerRequestHandler {
    private final ValueOf<PrivateHttpServerConfig> config;
    private final ValueOf<Optional<MetricsScraper>> meterRegistry;

    public MetricsHandler(ValueOf<PrivateHttpServerConfig> config, ValueOf<Optional<MetricsScraper>> meterRegistry) {
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
