package io.koraframework.database.jdbc.postgres.mapper.parameter;

import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import org.jspecify.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.function.Function;

/**
 * <b>Русский</b>: Конвертер массива Java в массив PostgreSQL указанного типа элемента.
 * Элементы {@code null} передаются как есть — массив PostgreSQL их допускает.
 * <hr>
 * <b>English</b>: Converter of a Java array into a PostgreSQL array of the given element type.
 * {@code null} elements are passed through as is — a PostgreSQL array permits them.
 */
public class PgArrayParameterColumnMapper<T> implements JdbcParameterColumnMapper<T[]> {

    private final String elementTypeName;
    private final Function<T, Object> elementWriter;

    public PgArrayParameterColumnMapper(String elementTypeName) {
        this(elementTypeName, element -> element);
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

        var elements = new Object[value.length];
        for (int i = 0; i < value.length; i++) {
            var element = value[i];
            elements[i] = (element == null) ? null : elementWriter.apply(element);
        }
        stmt.setArray(index, stmt.getConnection().createArrayOf(elementTypeName, elements));
    }
}
