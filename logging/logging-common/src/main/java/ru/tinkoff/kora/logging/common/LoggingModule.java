package ru.tinkoff.kora.logging.common;

import com.typesafe.config.Config;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

import javax.annotation.Nullable;
import java.util.Map;

public interface LoggingModule {
    default ConfigValueExtractor<LoggingConfig> loggingLevelConfigValueExtractor() {
        return new LoggingConfigValueExtractor();
    }

    default LoggingLevelRefresher loggingLevelRefresher(@Nullable LoggingConfig loggingConfig, LoggingLevelApplier loggingLevelApplier) {
        if (loggingConfig == null) {
            loggingConfig = new LoggingConfig(Map.of("ROOT", "info"));
        }
        return new LoggingLevelRefresher(loggingConfig, loggingLevelApplier);
    }

    default LoggingConfig loggingConfig(Config config, ConfigValueExtractor<LoggingConfig> extractor) {
        if (config.hasPath("logging")) {
            var value = config.getValue("logging");
            return extractor.extract(value);
        } else {
            return new LoggingConfig(Map.of());
        }
    }

    default ILoggerFactory loggerFactory() {
        return LoggerFactory.getILoggerFactory();
    }
}

