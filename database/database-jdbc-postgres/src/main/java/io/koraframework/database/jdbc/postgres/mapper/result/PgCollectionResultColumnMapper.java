package io.koraframework.database.jdbc.postgres.mapper.result;

import io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper;
import org.jspecify.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

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
        this(element -> (T) element);
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
        var result = new ArrayList<T>(elements.length);
        for (var element : elements) {
            result.add((element == null) ? null : elementReader.apply(element));
        }
        return result;
    }
}
