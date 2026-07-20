package io.koraframework.database.jdbc.mapper.parameter;

import io.koraframework.database.jdbc.EnumColumnData;
import org.jspecify.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public class EnumJdbcParameterColumnMapper<E extends Enum<E>, V> implements JdbcParameterColumnMapper<E> {

    private final EnumColumnData<E, V> enumColumnData;
    private final JdbcParameterColumnMapper<V> valueMapper;

    public EnumJdbcParameterColumnMapper(EnumColumnData<E, V> enumColumnData, JdbcParameterColumnMapper<V> valueMapper) {
        this.enumColumnData = enumColumnData;
        this.valueMapper = valueMapper;
    }

    @Override
    public void set(PreparedStatement stmt, int index, @Nullable E value) throws SQLException {
        var dbValue = value == null ? null : enumColumnData.valueGetter().apply(value);
        valueMapper.set(stmt, index, dbValue);
    }
}
