package io.koraframework.http.server.common.system;

import io.koraframework.application.graph.*;
import io.koraframework.http.common.body.*;
import io.koraframework.http.server.common.request.*;
import io.koraframework.http.server.common.response.*;
import io.koraframework.telemetry.common.*;

import java.io.*;
import java.util.*;

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
        return config.metricsPath();
    }

    @Override
    public HttpServerResponse handle(HttpServerRequest request) {
        var optionalRegistry = meterRegistry.get();
        var registry = optionalRegistry.isPresent() ? optionalRegistry.get() : null;
        if (registry == null) {
            return HttpServerResponse.of(200, HttpBody.plaintext("# Metric Scraper disabled"));
        }

        return HttpServerResponse.of(200, HttpBodyOutput.of("text/plain", new MetricsScraperBodyWriter(registry)));
    }

    private static final class MetricsScraperBodyWriter implements HttpBodyOutput.HttpBodyWriter {
        private final MetricsScraper scraper;

        public MetricsScraperBodyWriter(MetricsScraper scraper) {
            this.scraper = scraper;
        }

        @Override
        public void write(OutputStream os) throws IOException {
            scraper.scrape(os);
        }
    }
}
