package ru.tinkoff.kora.logging.common;

import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

import java.util.Map;

public interface LoggingModule {
    default ConfigValueExtractor<LoggingConfig> loggingLevelConfigValueExtractor() {
        return new LoggingConfigValueExtractor();
    }

    @Root
    default LoggingLevelRefresher loggingLevelRefresher(LoggingConfig loggingConfig, LoggingLevelApplier loggingLevelApplier) {
        return new LoggingLevelRefresher(loggingConfig, loggingLevelApplier);
    }

    default LoggingConfig loggingConfig(Config config, ConfigValueExtractor<LoggingConfig> extractor) {
        var value = config.get("logging");
        if (value == null) {
            return new LoggingConfig(Map.of());
        } else {
            return extractor.extract(value);
        }
    }

    default ILoggerFactory loggerFactory() {
        return LoggerFactory.getILoggerFactory();
    }
}

