package ru.tinkoff.kora.database.cassandra.mapper.parameter;

import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.Statement;

public interface CassandraStatementSetter {
    Statement<?> apply(BoundStatementBuilder stmt);
}
