package io.koraframework.database.jdbc.postgres.mapper.parameter;

import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import io.koraframework.database.jdbc.postgres.PgRange;
import org.jspecify.annotations.Nullable;
import org.postgresql.util.PGobject;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.function.Function;

/**
 * <b>Русский</b>: Конвертер {@link PgRange} в range-тип PostgreSQL с указанным именем типа.
 * <hr>
 * <b>English</b>: Converter of a {@link PgRange} into a PostgreSQL range type with the given type name.
 */
public class PgRangeParameterColumnMapper<T> implements JdbcParameterColumnMapper<PgRange<T>> {

    private final String rangeTypeName;
    private final Function<T, String> boundWriter;

    public PgRangeParameterColumnMapper(String rangeTypeName, Function<T, String> boundWriter) {
        this.rangeTypeName = rangeTypeName;
        this.boundWriter = boundWriter;
    }

    @Override
    public void set(PreparedStatement stmt, int index, @Nullable PgRange<T> value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, Types.OTHER);
            return;
        }

        var pgObject = new PGobject();
        pgObject.setType(rangeTypeName);
        pgObject.setValue(format(value));
        stmt.setObject(index, pgObject);
    }

    private String format(PgRange<T> range) {
        if (range.isEmpty()) {
            return "empty";
        }

        var builder = new StringBuilder();
        builder.append(range.lowerInclusive() ? '[' : '(');
        var lower = range.lower();
        if (lower != null) {
            appendQuoted(builder, boundWriter.apply(lower));
        }
        builder.append(',');
        var upper = range.upper();
        if (upper != null) {
            appendQuoted(builder, boundWriter.apply(upper));
        }
        builder.append(range.upperInclusive() ? ']' : ')');
        return builder.toString();
    }

    // границы всегда в кавычках: иначе PostgreSQL трактует запятую и скобки внутри границы как разделители
    private static void appendQuoted(StringBuilder builder, String bound) {
        builder.append('"');
        for (int i = 0; i < bound.length(); i++) {
            var c = bound.charAt(i);
            if (c == '"' || c == '\\') {
                builder.append('\\');
            }
            builder.append(c);
        }
        builder.append('"');
    }
}
