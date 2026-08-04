package io.koraframework.http.server.common.system;

import io.koraframework.application.graph.All;
import io.koraframework.application.graph.PromiseOf;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.common.liveness.LivenessProbe;
import io.koraframework.common.readiness.ReadinessProbe;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.http.server.common.HttpServerModule;
import io.koraframework.http.server.common.request.HttpServerRequestHandler;
import io.koraframework.telemetry.common.MetricsScraper;

import java.util.Optional;

public interface SystemHttpServerModule extends HttpServerModule {

    @SystemApi
    default SystemHttpServerConfig systemHttpServerConfig(Config config, ConfigValueMapper<SystemHttpServerConfig> mapper) {
        return mapper.mapOrThrow(config.get("httpServer.system"));
    }

    @SystemApi
    default HttpServerRequestHandler systemLivenessHttpServerRequestHandler(@SystemApi SystemHttpServerConfig config, All<PromiseOf<LivenessProbe>> probes) {
        return new LivenessHandler(config, probes);
    }

    @SystemApi
    default HttpServerRequestHandler systemReadinessHttpServerRequestHandler(@SystemApi SystemHttpServerConfig config, All<PromiseOf<ReadinessProbe>> probes) {
        return new ReadinessHandler(config, probes);
    }

    @SystemApi
    default HttpServerRequestHandler systemMetricsHttpServerRequestHandler(@SystemApi SystemHttpServerConfig config, ValueOf<Optional<MetricsScraper>> meterRegistry) {
        return new MetricsHandler(config, meterRegistry);
    }
}
