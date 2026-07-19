package io.koraframework.resilient.ratelimiter;

import io.koraframework.application.graph.RefreshListener;
import io.koraframework.resilient.ratelimiter.telemetry.RateLimiterTelemetryFactory;

import java.util.concurrent.ConcurrentHashMap;

final class KoraRateLimiterManager implements RateLimiterManager, RefreshListener {

    private final ConcurrentHashMap<String, RateLimiter> rateLimiterMap = new ConcurrentHashMap<>();
    private final RateLimiterConfig config;
    private final RateLimiterTelemetryFactory telemetryFactory;

    KoraRateLimiterManager(RateLimiterConfig config, RateLimiterTelemetryFactory telemetryFactory) {
        this.config = config;
        this.telemetryFactory = telemetryFactory;
    }

    @Override
    public RateLimiter get(String name) {
        return rateLimiterMap.computeIfAbsent(name, k -> new KoraRateLimiter(k, config.getNamedConfig(k), this.telemetryFactory.get(k, this.config.telemetry())));
    }

    @Override
    public void graphRefreshed() throws Exception {

    }
}
