package io.koraframework.database.jdbc.postgres.mapper.parameter;

import io.koraframework.database.jdbc.mapper.parameter.*;
import org.jspecify.annotations.*;

import java.sql.*;
import java.util.*;
import java.util.function.*;

import static io.koraframework.database.jdbc.postgres.mapper.parameter.PgArrayParameterColumnMapper.*;

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

    @SuppressWarnings("unchecked")
    public PgCollectionParameterColumnMapper(String elementTypeName) {
        this(elementTypeName, (Function<T, Object>) IdentityFunction.INSTANCE);
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

        final int size = value.size();
        if (size == 0) {
            stmt.setArray(index, stmt.getConnection().createArrayOf(elementTypeName, EMPTY_OBJECTS));
            return;
        }

        var elements = new Object[size];

        if (value instanceof RandomAccess && value instanceof List) {
            final List<T> list = (List<T>) value;
            for (int i = 0; i < size; i++) {
                var element = list.get(i);
                elements[i] = element == null ? null : elementWriter.apply(element);
            }
        } else {
            var i = 0;
            for (var element : value) {
                elements[i++] = element == null ? null : elementWriter.apply(element);
            }
        }

        stmt.setArray(index, stmt.getConnection().createArrayOf(elementTypeName, elements));
    }

    private static final class IdentityFunction<I> implements Function<I, Object> {
        private static final IdentityFunction<Object> INSTANCE = new IdentityFunction<>();

        @Override
        public Object apply(I t) {
            return t;
        }
    }
}
