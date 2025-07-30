package ru.tinkoff.kora.database.cassandra;

import com.datastax.oss.driver.api.core.CqlSessionBuilder;

public interface CassandraConfigurer {
    CqlSessionBuilder configure(CqlSessionBuilder builder);
}
