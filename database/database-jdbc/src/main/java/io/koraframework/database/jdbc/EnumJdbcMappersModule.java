package io.koraframework.database.jdbc;

import io.koraframework.database.jdbc.mapper.parameter.EnumJdbcParameterColumnMapper;
import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import io.koraframework.database.jdbc.mapper.result.EnumJdbcResultColumnMapper;
import io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper;
import io.koraframework.database.jdbc.mapper.result.JdbcRowMapper;
import io.koraframework.common.annotation.DefaultComponent;

public interface EnumJdbcMappersModule {

    @DefaultComponent
    default <E extends Enum<E>, V> JdbcParameterColumnMapper<E> enumJdbcParameterColumnMapper(
            EnumColumnData<E, V> enumColumnData, JdbcParameterColumnMapper<V> valueMapper) {
        return new EnumJdbcParameterColumnMapper<>(enumColumnData, valueMapper);
    }

    default <E extends Enum<E>, V> JdbcResultColumnMapper<E> enumJdbcResultColumnMapper(
            EnumColumnData<E, V> enumColumnData, JdbcResultColumnMapper<V> valueMapper) {
        return new EnumJdbcResultColumnMapper<>(enumColumnData, valueMapper);
    }

    default <E extends Enum<E>> JdbcRowMapper<E> enumJdbcRowMapper(JdbcResultColumnMapper<E> resultColumnMapper) {
        return row -> resultColumnMapper.apply(row, 1);
    }
}
