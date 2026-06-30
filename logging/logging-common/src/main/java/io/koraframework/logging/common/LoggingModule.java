package io.koraframework.logging.common;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Root;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

public interface LoggingModule {

    @Root
    default LoggingLevelRefresher loggingLevelRefresher(LoggingConfig loggingConfig, LoggingLevelApplier loggingLevelApplier) {
        return new LoggingLevelRefresher(loggingConfig, loggingLevelApplier);
    }

    default LoggingConfig loggingConfig(Config config, ConfigValueExtractor<LoggingConfig> extractor) {
        return extractor.extractOrThrow(config.get("logging"));
    }

    @DefaultComponent
    default ILoggerFactory loggerFactory() {
        return LoggerFactory.getILoggerFactory();
    }
}

