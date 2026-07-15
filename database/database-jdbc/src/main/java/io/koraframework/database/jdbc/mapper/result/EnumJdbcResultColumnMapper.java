package io.koraframework.database.jdbc.mapper.result;

import io.koraframework.database.jdbc.EnumColumnData;
import org.jspecify.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;

public class EnumJdbcResultColumnMapper<E extends Enum<E>, V> implements JdbcResultColumnMapper<E> {

    private final EnumColumnData<E, V> enumColumnData;
    private final JdbcResultColumnMapper<V> valueMapper;

    public EnumJdbcResultColumnMapper(EnumColumnData<E, V> enumColumnData, JdbcResultColumnMapper<V> valueMapper) {
        this.enumColumnData = enumColumnData;
        this.valueMapper = valueMapper;
    }

    @Override
    public @Nullable E apply(ResultSet row, int index) throws SQLException {
        var value = valueMapper.apply(row, index);
        if (value == null) {
            return null;
        }
        return enumColumnData.fromValueFactory().apply(value);
    }
}
