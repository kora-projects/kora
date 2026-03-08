package io.koraframework.database.flyway;

import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;

public interface FlywayJdbcDatabaseModule {

    default FlywayConfig flywayConfig(Config config, ConfigValueExtractor<FlywayConfig> extractor) {
        var value = config.get("flyway");
        return extractor.extract(value);
    }

    default FlywayJdbcDatabaseInterceptor flywayJdbcDatabaseInterceptor(FlywayConfig flywayConfig) {
        return new FlywayJdbcDatabaseInterceptor(flywayConfig);
    }
}
