package ru.tinkoff.kora.database.flyway;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.util.List;

@ConfigValueExtractor
public interface FlywayConfig {

    default List<String> locations() {
        return List.of("db/migration");
    }
}
