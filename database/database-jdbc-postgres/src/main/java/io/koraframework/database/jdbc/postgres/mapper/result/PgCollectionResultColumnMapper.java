package io.koraframework.database.jdbc.postgres.mapper.result;

import io.koraframework.database.jdbc.mapper.result.*;
import org.jspecify.annotations.*;

import java.sql.*;
import java.util.*;
import java.util.function.*;

/**
 * <b>Русский</b>: Конвертер массива PostgreSQL в {@link List}.
 * Элементы {@code null} передаются как есть.
 * <hr>
 * <b>English</b>: Converter of a PostgreSQL array into a {@link List}.
 * {@code null} elements are passed through as is.
 */
public class PgCollectionResultColumnMapper<T> implements JdbcResultColumnMapper<List<T>> {

    private final Function<Object, T> elementReader;

    @SuppressWarnings("unchecked")
    public PgCollectionResultColumnMapper() {
        this((Function<Object, T>) CastFunction.INSTANCE);
    }

    public PgCollectionResultColumnMapper(Function<Object, T> elementReader) {
        this.elementReader = elementReader;
    }

    @Override
    public @Nullable List<T> apply(ResultSet row, int index) throws SQLException {
        var array = row.getArray(index);
        if (row.wasNull() || array == null) {
            return null;
        }

        var elements = (Object[]) array.getArray();
        final int length = elements.length;
        var result = new ArrayList<T>(length);
        for (int i = 0; i < length; i++) {
            var element = elements[i];
            result.add(element == null ? null : elementReader.apply(element));
        }
        return result;
    }

    private static final class CastFunction<R> implements Function<Object, R> {
        private static final CastFunction<Object> INSTANCE = new CastFunction<>();

        @SuppressWarnings("unchecked")
        @Override
        public R apply(Object element) {
            return (R) element;
        }
    }
}
