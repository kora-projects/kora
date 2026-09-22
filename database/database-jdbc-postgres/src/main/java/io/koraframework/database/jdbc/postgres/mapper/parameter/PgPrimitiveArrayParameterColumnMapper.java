package io.koraframework.database.jdbc.postgres.mapper.parameter;

import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * <b>Русский</b>: Конвертер примитивного массива Java в массив PostgreSQL указанного типа элемента.
 * <hr>
 * <b>English</b>: Converter of a Java primitive array into a PostgreSQL array of the given element type.
 */
public final class PgPrimitiveArrayParameterColumnMapper<T> implements JdbcParameterColumnMapper<T> {

    private final String elementTypeName;

    public PgPrimitiveArrayParameterColumnMapper(String elementTypeName) {
        this.elementTypeName = elementTypeName;
    }

    @Override
    public void set(PreparedStatement stmt, int index, @Nullable T value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, Types.ARRAY);
            return;
        }

        var length = Array.getLength(value);
        var elements = new Object[length];
        for (int i = 0; i < length; i++) {
            elements[i] = Array.get(value, i);
        }
        stmt.setArray(index, stmt.getConnection().createArrayOf(elementTypeName, elements));
    }
}
