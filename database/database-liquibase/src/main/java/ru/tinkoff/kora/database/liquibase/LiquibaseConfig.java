package ru.tinkoff.kora.database.liquibase;


import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

@ConfigValueExtractor
public interface LiquibaseConfig {

    default String changelog() {
        return "db/changelog/db.changelog-master.xml";
    }
}
