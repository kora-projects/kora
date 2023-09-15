package ru.tinkoff.kora.logging.common;

import ru.tinkoff.kora.application.graph.Lifecycle;

public class LoggingLevelRefresher implements Lifecycle {
    private final LoggingConfig config;
    private final LoggingLevelApplier loggingLevelApplier;

    public LoggingLevelRefresher(LoggingConfig config, LoggingLevelApplier loggingLevelApplier) {
        this.config = config;
        this.loggingLevelApplier = loggingLevelApplier;
    }

    @Override
    public void init() {
        this.loggingLevelApplier.reset();
        for (var entry : config.levels().entrySet()) {
            this.loggingLevelApplier.apply(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void release() {}
}
