package io.koraframework.http.server.common;

import io.koraframework.application.graph.All;
import io.koraframework.application.graph.PromiseOf;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.common.DefaultComponent;
import io.koraframework.common.Tag;
import io.koraframework.common.liveness.LivenessProbe;
import io.koraframework.common.readiness.ReadinessProbe;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractionException;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.http.server.common.interceptor.HttpServerInterceptor;
import io.koraframework.http.server.common.request.HttpServerRequestHandler;
import io.koraframework.http.server.common.request.mapper.HttpParameterReaderModule;
import io.koraframework.http.server.common.request.mapper.HttpServerRequestMapperModule;
import io.koraframework.http.server.common.response.mapper.HttpServerResponseMapperModule;
import io.koraframework.http.server.common.router.HttpServerHandler;
import io.koraframework.http.server.common.system.*;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryFactory;
import io.koraframework.http.server.common.telemetry.impl.DefaultHttpServerTelemetryFactory;
import io.koraframework.telemetry.common.MetricsScraper;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public interface HttpServerModule extends HttpParameterReaderModule, HttpServerRequestMapperModule, HttpServerResponseMapperModule {

    default HttpServerConfig httpServerConfig(Config config, ConfigValueExtractor<HttpServerConfig> configValueExtractor) {
        var value = config.get("httpServer");
        var parsed = configValueExtractor.extract(value);
        if (parsed == null) {
            throw ConfigValueExtractionException.missingValueAfterParse(value);
        }
        return parsed;
    }

    default HttpServerHandler publicHttpServerHandler(All<HttpServerRequestHandler> handlers,
                                                      @Tag(HttpServerModule.class) All<HttpServerInterceptor> interceptors,
                                                      HttpServerConfig config) {
        return new HttpServerHandler(handlers, interceptors, config);
    }

    @DefaultComponent
    default HttpServerTelemetryFactory defaultHttpServerTelemetryFactory(@Nullable MeterRegistry meterRegistry, @Nullable Tracer tracer) {
        return new DefaultHttpServerTelemetryFactory(meterRegistry, tracer);
    }

    @SystemApi
    default HttpServerSystemConfig systemHttpServerConfig(Config config, ConfigValueExtractor<HttpServerSystemConfig> configValueExtractor) {
        var value = config.get("httpServer.system");
        var parsed = configValueExtractor.extract(value);
        if (parsed == null) {
            throw ConfigValueExtractionException.missingValueAfterParse(value);
        }
        return parsed;
    }

    @SystemApi
    default HttpServerRequestHandler systemLivenessHandler(@SystemApi ValueOf<HttpServerSystemConfig> config, All<PromiseOf<LivenessProbe>> probes) {
        return new LivenessHandler(config, probes);
    }

    @SystemApi
    default HttpServerRequestHandler systemReadinessHandler(@SystemApi ValueOf<HttpServerSystemConfig> config, All<PromiseOf<ReadinessProbe>> probes) {
        return new ReadinessHandler(config, probes);
    }

    @SystemApi
    default HttpServerRequestHandler systemMetricsHandler(@SystemApi ValueOf<HttpServerSystemConfig> config, ValueOf<Optional<MetricsScraper>> meterRegistry) {
        return new MetricsHandler(config, meterRegistry);
    }

    @SystemApi
    default HttpServerHandler systemHttpServerHandler(@Tag(SystemApi.class) All<HttpServerRequestHandler> handlers, @Tag(SystemApi.class) All<HttpServerInterceptor> interceptors, HttpServerConfig config) {
        return new HttpServerHandler(handlers, interceptors, config);
    }
}
