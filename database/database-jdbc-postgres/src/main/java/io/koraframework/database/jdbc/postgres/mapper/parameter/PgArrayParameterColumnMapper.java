package io.koraframework.database.jdbc.postgres.mapper.parameter;

import io.koraframework.database.jdbc.mapper.parameter.*;
import org.jspecify.annotations.*;

import java.sql.*;
import java.util.function.*;

/**
 * <b>Русский</b>: Конвертер массива Java в массив PostgreSQL указанного типа элемента.
 * Элементы {@code null} передаются как есть — массив PostgreSQL их допускает.
 * <hr>
 * <b>English</b>: Converter of a Java array into a PostgreSQL array of the given element type.
 * {@code null} elements are passed through as is — a PostgreSQL array permits them.
 */
public class PgArrayParameterColumnMapper<T> implements JdbcParameterColumnMapper<T[]> {

    public static final Object[] EMPTY_OBJECTS = new Object[0];

    private final String elementTypeName;
    private final Function<T, Object> elementWriter;

    @SuppressWarnings("unchecked")
    public PgArrayParameterColumnMapper(String elementTypeName) {
        this(elementTypeName, (Function<T, Object>) IdentityFunction.INSTANCE);
    }

    public PgArrayParameterColumnMapper(String elementTypeName, Function<T, Object> elementWriter) {
        this.elementTypeName = elementTypeName;
        this.elementWriter = elementWriter;
    }

    @Override
    public void set(PreparedStatement stmt, int index, T @Nullable [] value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, Types.ARRAY);
            return;
        }

        final int length = value.length;
        if (length == 0) {
            stmt.setArray(index, stmt.getConnection().createArrayOf(elementTypeName, EMPTY_OBJECTS));
            return;
        }

        var elements = new Object[length];
        for (int i = 0; i < length; i++) {
            var element = value[i];
            elements[i] = element == null ? null : elementWriter.apply(element);
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
