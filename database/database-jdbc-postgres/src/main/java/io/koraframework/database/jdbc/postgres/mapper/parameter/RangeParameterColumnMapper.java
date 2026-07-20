package io.koraframework.database.jdbc.postgres.mapper.parameter;

import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import io.koraframework.database.jdbc.postgres.PostgresRangeColumnData;
import io.koraframework.database.jdbc.postgres.Range;
import org.jspecify.annotations.Nullable;
import org.postgresql.util.PGobject;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

public class RangeParameterColumnMapper<T> implements JdbcParameterColumnMapper<Range<T>> {

    private final PostgresRangeColumnData<T> rangeColumnData;

    public RangeParameterColumnMapper(PostgresRangeColumnData<T> rangeColumnData) {
        this.rangeColumnData = rangeColumnData;
    }

    @Override
    public void set(PreparedStatement stmt, int index, @Nullable Range<T> range) throws SQLException {
        if (range == null) {
            stmt.setNull(index, Types.OTHER);
            return;
        }
        var pgObject = new PGobject();
        pgObject.setType(rangeColumnData.rangeTypeName());
        pgObject.setValue(format(range));
        stmt.setObject(index, pgObject);
    }

    private String format(Range<T> range) {
        var sb = new StringBuilder();
        sb.append(range.lowerInclusive() ? '[' : '(');
        if (range.lower() != null) {
            sb.append(quote(rangeColumnData.toBound().apply(range.lower())));
        }
        sb.append(',');
        if (range.upper() != null) {
            sb.append(quote(rangeColumnData.toBound().apply(range.upper())));
        }
        sb.append(range.upperInclusive() ? ']' : ')');
        return sb.toString();
    }

    private static String quote(String bound) {
        return '"' + bound.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
