package io.koraframework.database.liquibase;

import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;

public interface LiquibaseJdbcDatabaseModule {

    default LiquibaseConfig liquibaseConfig(Config config, ConfigValueExtractor<LiquibaseConfig> extractor) {
        var value = config.get("liquibase");
        return extractor.extract(value);
    }

    default LiquibaseJdbcDatabaseInterceptor liquibaseJdbcDatabaseInterceptor(LiquibaseConfig liquibaseConfig) {
        return new LiquibaseJdbcDatabaseInterceptor(liquibaseConfig);
    }
}
