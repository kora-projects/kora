package ru.tinkoff.kora.resilient.timeout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

final class KoraTimeoutManager implements TimeoutManager {

    private static final Logger logger = LoggerFactory.getLogger(KoraTimeoutManager.class);

    private final Map<String, Timeout> timeouterMap = new ConcurrentHashMap<>();
    private final TimeoutMetrics metrics;
    private final ExecutorService executor;
    private final TimeoutConfig config;

    KoraTimeoutManager(TimeoutMetrics metrics, ExecutorService executor, TimeoutConfig config) {
        this.metrics = metrics;
        this.executor = executor;
        this.config = config;
    }

    @Nonnull
    @Override
    public Timeout get(@Nonnull String name) {
        return timeouterMap.computeIfAbsent(name, (k) -> {
            var config = this.config.getNamedConfig(name);
            logger.debug("Creating Timeout named '{}' and config {}", name, config);
            return new KoraTimeout(name, config.duration().toNanos(), metrics, executor);
        });
    }
}
