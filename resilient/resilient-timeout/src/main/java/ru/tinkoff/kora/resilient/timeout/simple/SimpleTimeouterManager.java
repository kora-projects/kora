package ru.tinkoff.kora.resilient.timeout.simple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.resilient.timeout.Timeouter;
import ru.tinkoff.kora.resilient.timeout.TimeouterManager;
import ru.tinkoff.kora.resilient.timeout.telemetry.TimeoutMetrics;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

final class SimpleTimeouterManager implements TimeouterManager {

    private static final Logger logger = LoggerFactory.getLogger(SimpleTimeouterManager.class);

    private final Map<String, Timeouter> timeouterMap = new ConcurrentHashMap<>();
    private final TimeoutMetrics metrics;
    private final ExecutorService executor;
    private final SimpleTimeoutConfig config;

    SimpleTimeouterManager(TimeoutMetrics metrics, ExecutorService executor, SimpleTimeoutConfig config) {
        this.metrics = metrics;
        this.executor = executor;
        this.config = config;
    }

    @Nonnull
    @Override
    public Timeouter get(@Nonnull String name) {
        return timeouterMap.computeIfAbsent(name, (k) -> {
            var config = this.config.getNamedConfig(name);
            logger.debug("Creating Timeouter named '{}' and config {}", name, config);
            return new SimpleTimeouter(name, config.duration().toNanos(), metrics, executor);
        });
    }
}
