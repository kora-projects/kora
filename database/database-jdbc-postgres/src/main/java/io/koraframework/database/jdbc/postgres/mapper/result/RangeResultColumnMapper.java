package io.koraframework.database.jdbc.postgres.mapper.result;

import io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper;
import io.koraframework.database.jdbc.postgres.PostgresRangeColumnData;
import io.koraframework.database.jdbc.postgres.Range;
import org.jspecify.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;

public class RangeResultColumnMapper<T> implements JdbcResultColumnMapper<Range<T>> {

    private final PostgresRangeColumnData<T> rangeColumnData;

    public RangeResultColumnMapper(PostgresRangeColumnData<T> rangeColumnData) {
        this.rangeColumnData = rangeColumnData;
    }

    @Override
    public @Nullable Range<T> apply(ResultSet row, int index) throws SQLException {
        var value = row.getString(index);
        if (row.wasNull()) {
            return null;
        }
        if (value.equals("empty")) {
            return new Range<>(null, null, false, false);
        }
        var lowerInclusive = value.charAt(0) == '[';
        var upperInclusive = value.charAt(value.length() - 1) == ']';
        var inner = value.substring(1, value.length() - 1);
        var comma = inner.indexOf(',');
        var lowerStr = stripQuotes(inner.substring(0, comma));
        var upperStr = stripQuotes(inner.substring(comma + 1));
        var lower = lowerStr.isEmpty() ? null : rangeColumnData.fromBound().apply(lowerStr);
        var upper = upperStr.isEmpty() ? null : rangeColumnData.fromBound().apply(upperStr);
        return new Range<>(lower, upper, lowerInclusive, upperInclusive);
    }

    private static String stripQuotes(String s) {
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
