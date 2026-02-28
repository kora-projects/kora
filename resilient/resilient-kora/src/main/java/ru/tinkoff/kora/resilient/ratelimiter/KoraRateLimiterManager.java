package ru.tinkoff.kora.resilient.ratelimiter;

import java.util.concurrent.ConcurrentHashMap;

final class KoraRateLimiterManager implements RateLimiterManager {

    private final ConcurrentHashMap<String, RateLimiter> rateLimiterMap = new ConcurrentHashMap<>();
    private final RateLimiterConfig config;
    private final RateLimiterMetrics metrics;

    KoraRateLimiterManager(RateLimiterConfig config, RateLimiterMetrics metrics) {
        this.config = config;
        this.metrics = metrics;
    }

    @Override
    public RateLimiter get(String name) {
        return rateLimiterMap.computeIfAbsent(name, k -> new KoraRateLimiter(k, config.getNamedConfig(k), metrics));
    }
}
