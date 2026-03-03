package ru.tinkoff.kora.http.server.common;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.PromiseOf;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.liveness.LivenessProbe;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractionException;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.http.server.common.annotation.InternalApi;
import ru.tinkoff.kora.http.server.common.annotation.PrivateApi;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.common.privateapi.LivenessHandler;
import ru.tinkoff.kora.http.server.common.privateapi.MetricsHandler;
import ru.tinkoff.kora.http.server.common.privateapi.ReadinessHandler;
import ru.tinkoff.kora.http.server.common.router.HttpServerHandler;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetryFactory;
import ru.tinkoff.kora.http.server.common.telemetry.impl.DefaultHttpServerTelemetryFactory;
import ru.tinkoff.kora.telemetry.common.MetricsScraper;

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

    @InternalApi
    default InternalHttpServerConfig internalApiHttpServerConfig(Config config, ConfigValueExtractor<InternalHttpServerConfig> configValueExtractor) {
        var value = config.get("internalHttpServer");
        var parsed = configValueExtractor.extract(value);
        if (parsed == null) {
            throw ConfigValueExtractionException.missingValueAfterParse(value);
        }
        return parsed;
    }

    @InternalApi
    default HttpServerHandler internalApiHandler(@Tag(InternalApi.class) All<HttpServerRequestHandler> handlers, @Tag(InternalApi.class) All<HttpServerInterceptor> interceptors, HttpServerConfig config) {
        return new HttpServerHandler(handlers, interceptors, config);
    }

}
