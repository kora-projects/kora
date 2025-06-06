package ru.tinkoff.kora.resilient.timeout;

import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class KoraTimeoutManager implements TimeoutManager {

    private static final Logger logger = LoggerFactory.getLogger(KoraTimeoutManager.class);

    private final Map<String, Timeout> timeouterMap = new ConcurrentHashMap<>();
    private final TimeoutMetrics metrics;
    private final TimeoutExecutor timeoutExecutor;
    private final TimeoutConfig config;

    KoraTimeoutManager(TimeoutMetrics metrics, TimeoutExecutor timeoutExecutor, TimeoutConfig config) {
        this.metrics = metrics;
        this.timeoutExecutor = timeoutExecutor;
        this.config = config;
    }

    @Nonnull
    @Override
    public Timeout get(@Nonnull String name) {
        return timeouterMap.computeIfAbsent(name, (k) -> {
            var config = this.config.getNamedConfig(name);
            logger.debug("Creating Timeout named '{}' and config {}", name, config);
            return new KoraTimeout(name, config.duration().toNanos(), metrics, config, timeoutExecutor.executor());
        });
    }
}
