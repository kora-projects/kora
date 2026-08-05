package io.koraframework.database.liquibase;


import io.koraframework.config.common.annotation.ConfigMapper;

@ConfigMapper
public interface LiquibaseConfig {

    /**
     * @return Path to the main changelog file with migration definitions.
     */
    default String changelog() {
        return "db/changelog/db.changelog-master.xml";
    }
}
