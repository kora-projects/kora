package io.koraframework.database.jdbc.postgres.mapper.result;

import io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.IntFunction;

/**
 * <b>Русский</b>: Конвертер массива PostgreSQL в примитивный массив Java.
 * Элемент {@code NULL} не может быть представлен в примитивном массиве и приводит к ошибке.
 * <hr>
 * <b>English</b>: Converter of a PostgreSQL array into a Java primitive array.
 * A {@code NULL} element can't be represented in a primitive array and causes an error.
 */
public final class PgPrimitiveArrayResultColumnMapper<T> implements JdbcResultColumnMapper<T> {

    private final IntFunction<T> arrayFactory;

    public PgPrimitiveArrayResultColumnMapper(IntFunction<T> arrayFactory) {
        this.arrayFactory = arrayFactory;
    }

    @Override
    public @Nullable T apply(ResultSet row, int index) throws SQLException {
        var array = row.getArray(index);
        if (row.wasNull() || array == null) {
            return null;
        }

        var elements = (Object[]) array.getArray();
        var result = arrayFactory.apply(elements.length);
        for (int i = 0; i < elements.length; i++) {
            var element = elements[i];
            if (element == null) {
                throw new SQLException("PostgreSQL array contains NULL at index " + i + ", which can't be represented in a primitive array");
            }
            Array.set(result, i, element);
        }
        return result;
    }
}
