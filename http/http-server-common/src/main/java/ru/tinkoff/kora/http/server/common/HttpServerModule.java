package ru.tinkoff.kora.http.server.common;

import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.PromiseOf;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.liveness.LivenessProbe;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.common.router.PublicApiHandler;
import ru.tinkoff.kora.http.server.common.telemetry.*;

import jakarta.annotation.Nullable;
import java.util.Optional;

public interface HttpServerModule extends StringParameterReadersModule, HttpServerRequestMapperModule, HttpServerResponseMapperModule {

    default HttpServerConfig httpServerConfig(Config config, ConfigValueExtractor<HttpServerConfig> configValueExtractor) {
        return configValueExtractor.extract(config.get("httpServer"));
    }

    @DefaultComponent
    default DefaultHttpServerTelemetry defaultHttpServerTelemetry(@Nullable HttpServerMetrics metricWriter, @Nullable HttpServerLogger logger, @Nullable HttpServerTracer tracer) {
        return new DefaultHttpServerTelemetry(metricWriter, logger, tracer);
    }

    default PrivateApiHandler privateApiHandler(ValueOf<HttpServerConfig> config,
                                                ValueOf<Optional<PrivateApiMetrics>> meterRegistry,
                                                All<PromiseOf<ReadinessProbe>> readinessProbes,
                                                All<PromiseOf<LivenessProbe>> livenessProbes) {
        return new PrivateApiHandler(config, meterRegistry, readinessProbes, livenessProbes);
    }

    default PublicApiHandler publicApiHandler(All<HttpServerRequestHandler> handlers,
                                              @Tag(HttpServerModule.class) All<HttpServerInterceptor> interceptors,
                                              HttpServerTelemetry telemetry,
                                              HttpServerConfig config) {
        return new PublicApiHandler(handlers, interceptors, telemetry, config);
    }

    default HttpServerLogger httpServerLogger() {
        return new Slf4jHttpServerLogger();
    }
}
