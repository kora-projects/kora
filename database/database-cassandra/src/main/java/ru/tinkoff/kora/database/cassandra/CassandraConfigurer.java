package ru.tinkoff.kora.database.cassandra;

import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.ProgrammaticDriverConfigLoaderBuilder;

public interface CassandraConfigurer {
    CqlSessionBuilder configure(CqlSessionBuilder builder, ProgrammaticDriverConfigLoaderBuilder loaderBuilder);
}
