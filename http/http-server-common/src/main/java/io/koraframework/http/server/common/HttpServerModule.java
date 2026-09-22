package io.koraframework.http.server.common;

import io.koraframework.application.graph.All;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Tag;
import io.koraframework.http.server.common.interceptor.HttpServerInterceptor;
import io.koraframework.http.server.common.request.HttpServerRequestHandler;
import io.koraframework.http.server.common.request.mapper.HttpServerParameterReaderModule;
import io.koraframework.http.server.common.request.mapper.HttpServerRequestMapperModule;
import io.koraframework.http.server.common.response.mapper.HttpServerResponseMapperModule;
import io.koraframework.http.server.common.router.HttpServerRouter;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryFactory;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetry;
import io.koraframework.http.server.common.telemetry.impl.DefaultHttpServerBodyConverter;
import io.koraframework.http.server.common.telemetry.impl.DefaultHttpServerLoggerFactory;
import io.koraframework.http.server.common.telemetry.impl.DefaultHttpServerMetricsFactory;
import io.koraframework.http.server.common.telemetry.impl.DefaultHttpServerTelemetryFactory;
import io.koraframework.logging.common.masking.MaskingStrategy;
import io.koraframework.logging.common.masking.raw.DataMasker;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

import java.util.stream.StreamSupport;

public interface HttpServerModule extends HttpServerParameterReaderModule, HttpServerRequestMapperModule, HttpServerResponseMapperModule {

    @Tag(HttpServerTelemetry.class)
    @DefaultComponent
    default MaskingStrategy defaultHttpServerArgMaskingStrategy() {
        return value -> "***";
    }

    @DefaultComponent
    default DefaultHttpServerLoggerFactory defaultHttpServerLoggerFactory(@Tag(HttpServerTelemetry.class) MaskingStrategy maskingStrategy) {
        return new DefaultHttpServerLoggerFactory(maskingStrategy);
    }

    @DefaultComponent
    default DefaultHttpServerBodyConverter defaultHttpServerBodyConverter(@Tag(HttpServerTelemetry.class) All<DataMasker> dataMaskers) {
        return new DefaultHttpServerBodyConverter(StreamSupport.stream(dataMaskers.spliterator(), false).toList());
    }

    @DefaultComponent
    default HttpServerTelemetryFactory defaultHttpServerTelemetryFactory(@Nullable MeterRegistry meterRegistry,
                                                                         @Nullable Tracer tracer,
                                                                         @Nullable DefaultHttpServerLoggerFactory loggerFactory,
                                                                         @Nullable DefaultHttpServerMetricsFactory metricsFactory,
                                                                         DefaultHttpServerBodyConverter bodyLogger) {
        return new DefaultHttpServerTelemetryFactory(meterRegistry, tracer, loggerFactory, metricsFactory, bodyLogger);
    }

    default HttpServerRouter publicHttpApiRouter(All<HttpServerRequestHandler> handlers,
                                                 @Tag(HttpServer.class) All<HttpServerInterceptor> interceptors,
                                                 HttpServerConfig config) {
        return new HttpServerRouter(handlers, interceptors, config);
    }
}
