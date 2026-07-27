package ru.tinkoff.kora.database.liquibase;


import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

@ConfigValueExtractor
public interface LiquibaseConfig {

    /**
     * @return Path to the main changelog file with migration definitions.
     */
    default String changelog() {
        return "db/changelog/db.changelog-master.xml";
    }
}
