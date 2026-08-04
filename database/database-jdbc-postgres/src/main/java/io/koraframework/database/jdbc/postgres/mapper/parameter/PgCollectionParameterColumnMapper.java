package io.koraframework.database.jdbc.postgres.mapper.parameter;

import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import org.jspecify.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collection;
import java.util.function.Function;

/**
 * <b>Русский</b>: Конвертер {@link Collection} в массив PostgreSQL указанного типа элемента.
 * Элементы {@code null} передаются как есть — массив PostgreSQL их допускает.
 * <hr>
 * <b>English</b>: Converter of a {@link Collection} into a PostgreSQL array of the given element type.
 * {@code null} elements are passed through as is — a PostgreSQL array permits them.
 */
public class PgCollectionParameterColumnMapper<T, C extends Collection<T>> implements JdbcParameterColumnMapper<C> {

    private final String elementTypeName;
    private final Function<T, Object> elementWriter;

    public PgCollectionParameterColumnMapper(String elementTypeName) {
        this(elementTypeName, element -> element);
    }

    public PgCollectionParameterColumnMapper(String elementTypeName, Function<T, Object> elementWriter) {
        this.elementTypeName = elementTypeName;
        this.elementWriter = elementWriter;
    }

    @Override
    public void set(PreparedStatement stmt, int index, @Nullable C value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, Types.ARRAY);
            return;
        }

        var elements = new Object[value.size()];
        var i = 0;
        for (var element : value) {
            elements[i++] = (element == null) ? null : elementWriter.apply(element);
        }
        stmt.setArray(index, stmt.getConnection().createArrayOf(elementTypeName, elements));
    }
}
