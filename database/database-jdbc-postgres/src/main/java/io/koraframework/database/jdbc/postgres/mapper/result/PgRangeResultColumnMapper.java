package io.koraframework.database.jdbc.postgres.mapper.result;

import io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper;
import io.koraframework.database.jdbc.postgres.PgRange;
import org.jspecify.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Function;

/**
 * <b>Русский</b>: Конвертер range-типа PostgreSQL в {@link PgRange}.
 * <hr>
 * <b>English</b>: Converter of a PostgreSQL range type into a {@link PgRange}.
 */
public class PgRangeResultColumnMapper<T> implements JdbcResultColumnMapper<PgRange<T>> {

    private static final String EMPTY_RANGE = "empty";

    private final Function<String, T> boundReader;

    public PgRangeResultColumnMapper(Function<String, T> boundReader) {
        this.boundReader = boundReader;
    }

    @Override
    public @Nullable PgRange<T> apply(ResultSet row, int index) throws SQLException {
        var value = row.getString(index);
        if (row.wasNull() || value == null) {
            return null;
        }
        if (value.equals(EMPTY_RANGE)) {
            return PgRange.empty();
        }
        if (value.length() < 3) {
            throw new SQLException("Illegal PostgreSQL range value: " + value);
        }

        var lowerInclusive = value.charAt(0) == '[';
        var upperInclusive = value.charAt(value.length() - 1) == ']';

        String lowerBound = null;
        var bound = new StringBuilder();
        var boundPresent = false;
        var quoted = false;
        var separatorFound = false;
        for (int i = 1, end = value.length() - 1; i < end; i++) {
            var c = value.charAt(i);
            if (quoted) {
                if (c == '\\' && i + 1 < end) {
                    bound.append(value.charAt(++i));
                } else if (c == '"') {
                    quoted = false;
                } else {
                    bound.append(c);
                }
            } else if (c == '"') {
                quoted = true;
                boundPresent = true;
            } else if (c == ',' && !separatorFound) {
                lowerBound = boundPresent ? bound.toString() : null;
                bound.setLength(0);
                boundPresent = false;
                separatorFound = true;
            } else {
                bound.append(c);
                boundPresent = true;
            }
        }
        if (!separatorFound) {
            throw new SQLException("Illegal PostgreSQL range value: " + value);
        }
        var upperBound = boundPresent ? bound.toString() : null;

        return new PgRange<T>(
            (lowerBound == null) ? null : boundReader.apply(lowerBound),
            (upperBound == null) ? null : boundReader.apply(upperBound),
            lowerInclusive,
            upperInclusive);
    }
}
