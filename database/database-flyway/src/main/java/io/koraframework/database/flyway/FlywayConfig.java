package io.koraframework.database.flyway;

import io.koraframework.config.common.annotation.ConfigValueExtractor;

import java.util.List;
import java.util.Map;

@ConfigValueExtractor
public interface FlywayConfig {

    default boolean enabled() {
        return true;
    }

    default List<String> locations() {
        return List.of("db/migration");
    }

    default boolean executeInTransaction() {
        return true;
    }

    default boolean validateOnMigrate() {
        return true;
    }

    default boolean mixed() {
        return false;
    }

    default Map<String, String> configurationProperties() {
        return Map.of();
    }
}
