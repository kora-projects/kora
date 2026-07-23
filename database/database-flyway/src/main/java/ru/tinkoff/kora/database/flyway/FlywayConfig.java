package ru.tinkoff.kora.database.flyway;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.util.List;
import java.util.Map;

@ConfigValueExtractor
public interface FlywayConfig {

    /**
     * @return Whether to execute migrations during JdbcDatabase initialization.
     */
    default boolean enabled() {
        return true;
    }

    /**
     * @return Paths to directories with migration scripts.
     */
    default List<String> locations() {
        return List.of("db/migration");
    }

    /**
     * @return Whether to execute migrations inside a transaction when supported by the database and the SQL operations themselves.
     */
    default boolean executeInTransaction() {
        return true;
    }

    /**
     * @return Whether to validate checksums of already applied migrations before executing new ones.
     */
    default boolean validateOnMigrate() {
        return true;
    }

    /**
     * @return Whether to allow mixing transactional and non-transactional SQL operations in one migration, executing the whole migration without a transaction.
     */
    default boolean mixed() {
        return false;
    }

    /**
     * @return Additional Flyway key-value properties for settings that have no separate Kora configuration option.
     */
    default Map<String, String> configurationProperties() {
        return Map.of();
    }
}
