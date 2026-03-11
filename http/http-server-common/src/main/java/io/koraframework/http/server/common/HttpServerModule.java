package io.koraframework.http.server.common;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;
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
import io.koraframework.http.server.common.annotation.PrivateApi;
import io.koraframework.http.server.common.handler.HttpServerRequestHandler;
import io.koraframework.http.server.common.privateapi.LivenessHandler;
import io.koraframework.http.server.common.privateapi.MetricsHandler;
import io.koraframework.http.server.common.privateapi.ReadinessHandler;
import io.koraframework.http.server.common.router.HttpServerHandler;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryFactory;
import io.koraframework.http.server.common.telemetry.impl.DefaultHttpServerTelemetryFactory;
import io.koraframework.telemetry.common.MetricsScraper;

import java.util.Optional;

public interface HttpServerModule extends StringParameterReadersModule, HttpServerRequestMapperModule, HttpServerResponseMapperModule {

    default HttpServerConfig httpServerConfig(Config config, ConfigValueExtractor<HttpServerConfig> configValueExtractor) {
        var value = config.get("httpServer");
        var parsed = configValueExtractor.extract(value);
        if (parsed == null) {
            throw ConfigValueExtractionException.missingValueAfterParse(value);
        }
        return parsed;
    }

    default HttpServerHandler publicApiHandler(All<HttpServerRequestHandler> handlers,
                                               @Tag(HttpServerModule.class) All<HttpServerInterceptor> interceptors,
                                               HttpServerConfig config) {
        return new HttpServerHandler(handlers, interceptors, config);
    }

    @DefaultComponent
    default HttpServerTelemetryFactory defaultHttpServerTelemetryFactory(@Nullable MeterRegistry meterRegistry, @Nullable Tracer tracer) {
        return new DefaultHttpServerTelemetryFactory(meterRegistry, tracer);
    }

    @PrivateApi
    default PrivateHttpServerConfig privateApiHttpServerConfig(Config config, ConfigValueExtractor<PrivateHttpServerConfig> configValueExtractor) {
        var value = config.get("privateHttpServer");
        var parsed = configValueExtractor.extract(value);
        if (parsed == null) {
            throw ConfigValueExtractionException.missingValueAfterParse(value);
        }
        return parsed;
    }

    @PrivateApi
    default HttpServerRequestHandler livenessHandler(@PrivateApi ValueOf<PrivateHttpServerConfig> config, All<PromiseOf<LivenessProbe>> probes) {
        return new LivenessHandler(config, probes);
    }

    @PrivateApi
    default HttpServerRequestHandler readinessHandler(@PrivateApi ValueOf<PrivateHttpServerConfig> config, All<PromiseOf<ReadinessProbe>> probes) {
        return new ReadinessHandler(config, probes);
    }

    @PrivateApi
    default HttpServerRequestHandler metricsHandler(@PrivateApi ValueOf<PrivateHttpServerConfig> config, ValueOf<Optional<MetricsScraper>> meterRegistry) {
        return new MetricsHandler(config, meterRegistry);
    }

    @PrivateApi
    default HttpServerHandler privateApiHandler(@Tag(PrivateApi.class) All<HttpServerRequestHandler> handlers, @Tag(PrivateApi.class) All<HttpServerInterceptor> interceptors, HttpServerConfig config) {
        return new HttpServerHandler(handlers, interceptors, config);
    }

}
