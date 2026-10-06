package io.koraframework.logging.common;

import io.koraframework.application.graph.Lifecycle;
import io.koraframework.application.graph.RefreshListener;
import io.koraframework.application.graph.ValueOf;

/**
 * Applies logging levels on start and after a committed graph refresh that changed the config, so a rejected config never reaches the loggers.
 */
public class LoggingLevelRefresher implements Lifecycle, RefreshListener {
    private final ValueOf<LoggingConfig> config;
    private final LoggingLevelApplier loggingLevelApplier;
    private LoggingConfig applied;

    public LoggingLevelRefresher(ValueOf<LoggingConfig> config, LoggingLevelApplier loggingLevelApplier) {
        this.config = config;
        this.loggingLevelApplier = loggingLevelApplier;
    }

    @Override
    public void init() {
        this.applyLevels(config.get());
    }

    @Override
    public void graphRefreshed() {
        var current = config.get();
        if (!current.equals(this.applied)) {
            this.applyLevels(current);
        }
    }

    private void applyLevels(LoggingConfig current) {
        this.loggingLevelApplier.reset();
        for (var entry : current.levels().entrySet()) {
            this.loggingLevelApplier.apply(entry.getKey(), entry.getValue());
        }
        this.applied = current;
    }

    @Override
    public void release() {}
}
