package ru.tinkoff.kora.database.cassandra.mapper.result;

import com.datastax.oss.driver.api.core.cql.Row;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.database.common.RowMapper;

public interface CassandraRowMapper<T> extends Mapping.MappingFunction, RowMapper<T> {
    T apply(Row row);
}
