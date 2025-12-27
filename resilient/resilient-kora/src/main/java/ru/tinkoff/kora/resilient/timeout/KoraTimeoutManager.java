package ru.tinkoff.kora.resilient.timeout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class KoraTimeoutManager implements TimeoutManager {

    private static final Logger logger = LoggerFactory.getLogger(KoraTimeoutManager.class);

    private final Map<String, Timeout> timeouterMap = new ConcurrentHashMap<>();
    private final TimeoutMetrics metrics;
    private final TimeoutConfig config;

    KoraTimeoutManager(TimeoutMetrics metrics, TimeoutConfig config) {
        this.metrics = metrics;
        this.config = config;
    }

    @Override
    public Timeout get(String name) {
        return timeouterMap.computeIfAbsent(name, (k) -> {
            var config = this.config.getNamedConfig(name);
            logger.debug("Creating Timeout named '{}' and config {}", name, config);
            return new KoraTimeout(name, config.duration().toNanos(), metrics, config);
        });
    }
}
