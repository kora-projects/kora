package ru.tinkoff.kora.logging.common;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.common.util.ReactorUtils;

public class LoggingLevelRefresher implements Lifecycle {
    private final LoggingConfig config;
    private final LoggingLevelApplier loggingLevelApplier;

    public LoggingLevelRefresher(LoggingConfig config, LoggingLevelApplier loggingLevelApplier) {
        this.config = config;
        this.loggingLevelApplier = loggingLevelApplier;
    }

    @Override
    public Mono<Void> init() {
        return ReactorUtils.ioMono(() -> {
            this.loggingLevelApplier.reset();
            for (var entry : config.levels().entrySet()) {
                this.loggingLevelApplier.apply(entry.getKey(), entry.getValue());
            }
        });
    }

    @Override
    public Mono<Void> release() {
        return Mono.empty();
    }
}
