package io.koraframework.database.jdbc.postgres.mapper.parameter;

import io.koraframework.database.jdbc.EnumColumnData;
import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import org.jspecify.annotations.Nullable;
import org.postgresql.util.PGobject;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public class PostgresEnumJdbcParameterColumnMapper<E extends Enum<E>, V> implements JdbcParameterColumnMapper<E> {

    private final EnumColumnData<E, V> enumColumnData;
    private final JdbcParameterColumnMapper<V> valueMapper;

    public PostgresEnumJdbcParameterColumnMapper(EnumColumnData<E, V> enumColumnData, JdbcParameterColumnMapper<V> valueMapper) {
        this.enumColumnData = enumColumnData;
        this.valueMapper = valueMapper;
    }

    @Override
    public void set(PreparedStatement stmt, int index, @Nullable E value) throws SQLException {
        var sqlTypeName = enumColumnData.sqlTypeName();
        if (sqlTypeName != null) {
            var pgObject = new PGobject();
            pgObject.setType(sqlTypeName);
            pgObject.setValue(value == null ? null : String.valueOf(enumColumnData.valueGetter().apply(value)));
            stmt.setObject(index, pgObject);
        } else {
            var dbValue = value == null ? null : enumColumnData.valueGetter().apply(value);
            valueMapper.set(stmt, index, dbValue);
        }
    }
}
