package io.koraframework.http.server.common;

import io.koraframework.application.graph.All;
import io.koraframework.application.graph.PromiseOf;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Tag;
import io.koraframework.common.liveness.LivenessProbe;
import io.koraframework.common.readiness.ReadinessProbe;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.http.server.common.interceptor.HttpServerInterceptor;
import io.koraframework.http.server.common.request.HttpServerRequestHandler;
import io.koraframework.http.server.common.request.mapper.HttpServerParameterReaderModule;
import io.koraframework.http.server.common.request.mapper.HttpServerRequestMapperModule;
import io.koraframework.http.server.common.response.mapper.HttpServerResponseMapperModule;
import io.koraframework.http.server.common.router.HttpServerHandler;
import io.koraframework.http.server.common.system.*;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryFactory;
import io.koraframework.http.server.common.telemetry.impl.DefaultHttpServerBodyConverter;
import io.koraframework.http.server.common.telemetry.impl.DefaultHttpServerLoggerFactory;
import io.koraframework.http.server.common.telemetry.impl.DefaultHttpServerMetricsFactory;
import io.koraframework.http.server.common.telemetry.impl.DefaultHttpServerTelemetryFactory;
import io.koraframework.telemetry.common.MetricsScraper;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public interface HttpServerModule extends HttpServerParameterReaderModule, HttpServerRequestMapperModule, HttpServerResponseMapperModule {

    default HttpServerConfig publicHttpServerConfig(Config config, ConfigValueMapper<HttpServerConfig> mapper) {
        return mapper.mapOrThrow(config.get("httpServer"));
    }

    default HttpServerHandler publicHttpServerHandler(All<HttpServerRequestHandler> handlers,
                                                      @Tag(HttpServerModule.class) All<HttpServerInterceptor> interceptors,
                                                      HttpServerConfig config) {
        return new HttpServerHandler(handlers, interceptors, config);
    }

    @DefaultComponent
    default HttpServerTelemetryFactory defaultHttpServerTelemetryFactory(@Nullable MeterRegistry meterRegistry,
                                                                         @Nullable Tracer tracer,
                                                                         @Nullable DefaultHttpServerLoggerFactory loggerFactory,
                                                                         @Nullable DefaultHttpServerMetricsFactory metricsFactory,
                                                                         @Nullable DefaultHttpServerBodyConverter bodyLogger) {
        return new DefaultHttpServerTelemetryFactory(meterRegistry, tracer, loggerFactory, metricsFactory, bodyLogger);
    }

    @SystemApi
    default HttpServerSystemConfig systemHttpServerConfig(Config config, ConfigValueMapper<HttpServerSystemConfig> mapper) {
        return mapper.mapOrThrow(config.get("httpServer.system"));
    }

    @SystemApi
    default HttpServerRequestHandler systemLivenessHttpServerRequestHandler(@SystemApi ValueOf<HttpServerSystemConfig> config, All<PromiseOf<LivenessProbe>> probes) {
        return new LivenessHandler(config, probes);
    }

    @SystemApi
    default HttpServerRequestHandler systemReadinessHttpServerRequestHandler(@SystemApi ValueOf<HttpServerSystemConfig> config, All<PromiseOf<ReadinessProbe>> probes) {
        return new ReadinessHandler(config, probes);
    }

    @SystemApi
    default HttpServerRequestHandler systemMetricsHttpServerRequestHandler(@SystemApi ValueOf<HttpServerSystemConfig> config, ValueOf<Optional<MetricsScraper>> meterRegistry) {
        return new MetricsHandler(config, meterRegistry);
    }

    @SystemApi
    default HttpServerHandler systemHttpServerHandler(@SystemApi All<HttpServerRequestHandler> handlers, @SystemApi All<HttpServerInterceptor> interceptors, HttpServerConfig config) {
        return new HttpServerHandler(handlers, interceptors, config);
    }
}
