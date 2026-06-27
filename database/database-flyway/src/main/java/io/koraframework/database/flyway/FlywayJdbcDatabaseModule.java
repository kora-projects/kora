package io.koraframework.database.flyway;

import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;

public interface FlywayJdbcDatabaseModule {

    default FlywayConfig flywayConfig(Config config, ConfigValueExtractor<FlywayConfig> extractor) {
        return extractor.extractOrThrow(config.get("flyway"));
    }

    default FlywayJdbcDatabaseInterceptor flywayJdbcDatabaseInterceptor(FlywayConfig flywayConfig) {
        return new FlywayJdbcDatabaseInterceptor(flywayConfig);
    }
}
