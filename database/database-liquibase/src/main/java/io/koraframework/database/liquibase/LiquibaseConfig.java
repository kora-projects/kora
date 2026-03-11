package io.koraframework.database.liquibase;


import io.koraframework.config.common.annotation.ConfigValueExtractor;

@ConfigValueExtractor
public interface LiquibaseConfig {

    default String changelog() {
        return "db/changelog/db.changelog-master.xml";
    }
}
