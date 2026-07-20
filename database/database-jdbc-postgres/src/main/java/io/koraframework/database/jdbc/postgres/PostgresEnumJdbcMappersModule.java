package io.koraframework.database.jdbc.postgres;

import io.koraframework.database.jdbc.EnumColumnData;
import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import io.koraframework.database.jdbc.postgres.mapper.parameter.PostgresEnumJdbcParameterColumnMapper;

public interface PostgresEnumJdbcMappersModule {

    default <E extends Enum<E>, V> JdbcParameterColumnMapper<E> postgresEnumJdbcParameterColumnMapper(
            EnumColumnData<E, V> enumColumnData, JdbcParameterColumnMapper<V> valueMapper) {
        return new PostgresEnumJdbcParameterColumnMapper<>(enumColumnData, valueMapper);
    }
}
