package io.koraframework.database.jdbc.postgres.mapper.result;

import io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper;
import org.jspecify.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * <b>Русский</b>: Конвертер массива PostgreSQL в массив Java.
 * Элементы {@code null} передаются как есть, поэтому тип элемента должен быть боксированным.
 * <hr>
 * <b>English</b>: Converter of a PostgreSQL array into a Java array.
 * {@code null} elements are passed through as is, hence the element type must be a boxed one.
 */
public class PgArrayResultColumnMapper<T> implements JdbcResultColumnMapper<T[]> {

    private final IntFunction<T[]> arrayFactory;
    private final Function<Object, T> elementReader;

    @SuppressWarnings("unchecked")
    public PgArrayResultColumnMapper(IntFunction<T[]> arrayFactory) {
        this(arrayFactory, element -> (T) element);
    }

    public PgArrayResultColumnMapper(IntFunction<T[]> arrayFactory, Function<Object, T> elementReader) {
        this.arrayFactory = arrayFactory;
        this.elementReader = elementReader;
    }

    @Override
    public T @Nullable [] apply(ResultSet row, int index) throws SQLException {
        var array = row.getArray(index);
        if (row.wasNull() || array == null) {
            return null;
        }

        var elements = (Object[]) array.getArray();
        var result = arrayFactory.apply(elements.length);
        for (int i = 0; i < elements.length; i++) {
            var element = elements[i];
            result[i] = (element == null) ? null : elementReader.apply(element);
        }
        return result;
    }
}
