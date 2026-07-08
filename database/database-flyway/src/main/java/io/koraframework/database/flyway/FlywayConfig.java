package io.koraframework.database.flyway;

import io.koraframework.config.common.annotation.ConfigMapper;

import java.util.List;
import java.util.Map;

@ConfigMapper
public interface FlywayConfig {

    default boolean enabled() {
        return true;
    }

    default Mode mode() {
        return Mode.MIGRATE;
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

    enum Mode {
        MIGRATE,
        REPAIR,
        CLEAN_MIGRATE
    }
}
