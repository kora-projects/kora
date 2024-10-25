package ru.tinkoff.kora.database.common.annotation.processor.jdbc;

import org.mockito.Mockito;
import ru.tinkoff.kora.database.common.annotation.processor.AbstractExtensionTest;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

abstract class AbstractJdbcEntityTest extends AbstractExtensionTest {

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import jakarta.annotation.Nullable;
            import ru.tinkoff.kora.database.common.annotation.Embedded;
            import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcRowMapper;
            """;
    }

    public ResultSet mockResultSet(JdbcColumn<?>... columns) throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        var wasNulls = new Boolean[columns.length];
        var wasNullCounter = 0;
        var hasNext = new Boolean[columns.length];
        Arrays.fill(hasNext, true);
        if (columns.length > 0) {
            hasNext[columns.length - 1] = false;
        }
        for (int i = 0; i < columns.length; i++) {
            var column = columns[i];
            if (column.primitive()) {
                wasNulls[wasNullCounter++] = column.value == null;
            }
            column.mock(rs, i + 1);
        }
        if (wasNulls[0] != null) {
            when(rs.wasNull()).thenReturn(wasNulls[0], Arrays.copyOfRange(wasNulls, 1, wasNullCounter));
        }
        when(rs.next()).thenReturn(true, false);
        return rs;
    }

    public interface SqlBiFunction<P1, P2, R> {
        R apply(P1 p1, P2 p2) throws SQLException;
    }

    protected record JdbcColumn<T>(String column, JdbcEntityTest.SqlBiFunction<ResultSet, Integer, T> extractor, T value, boolean primitive) {
        void mock(ResultSet rs, int idx) throws SQLException {
            when(rs.findColumn(column)).thenReturn(idx);
            extractor.apply(doAnswer(invocation -> value).when(rs), idx);
        }
    }

    protected <T> JdbcColumn<T> of(String column, JdbcEntityTest.SqlBiFunction<ResultSet, Integer, T> extractor, T value) {
        return new JdbcColumn<>(column, extractor, value, false);
    }

    protected JdbcColumn<String> of(String column, String value) {
        return new JdbcColumn<>(column, ResultSet::getString, value, false);
    }

    protected JdbcColumn<Integer> of(String column, Integer value) {
        return new JdbcColumn<>(column, ResultSet::getInt, value, true);
    }
}
