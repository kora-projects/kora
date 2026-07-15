package io.koraframework.database.jdbc.postgres;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.postgresql.util.PGobject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgresRangeJdbcMappersModuleTest {

    private final PostgresRangeJdbcMappersModule module = new PostgresRangeJdbcMappersModule() {};

    @Test
    void writesClosedOpenRange() throws SQLException {
        var stmt = Mockito.mock(PreparedStatement.class);
        var mapper = module.rangeJdbcParameterColumnMapper(module.int4RangeColumnData());

        mapper.set(stmt, 1, Range.closedOpen(1, 10));

        var captor = ArgumentCaptor.forClass(PGobject.class);
        verify(stmt).setObject(eq(1), captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("int4range");
        assertThat(captor.getValue().getValue()).isEqualTo("[\"1\",\"10\")");
    }

    @Test
    void writesUnboundedUpper() throws SQLException {
        var stmt = Mockito.mock(PreparedStatement.class);
        var mapper = module.rangeJdbcParameterColumnMapper(module.int8RangeColumnData());

        mapper.set(stmt, 1, Range.closedOpen(5L, null));

        var captor = ArgumentCaptor.forClass(PGobject.class);
        verify(stmt).setObject(eq(1), captor.capture());
        assertThat(captor.getValue().getValue()).isEqualTo("[\"5\",)");
    }

    @Test
    void writesNull() throws SQLException {
        var stmt = Mockito.mock(PreparedStatement.class);
        var mapper = module.rangeJdbcParameterColumnMapper(module.int4RangeColumnData());

        mapper.set(stmt, 1, null);

        verify(stmt).setNull(1, Types.OTHER);
    }

    @Test
    void readsClosedOpenRange() throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        when(rs.getString(1)).thenReturn("[1,10)");
        when(rs.wasNull()).thenReturn(false);
        var mapper = module.rangeJdbcResultColumnMapper(module.int4RangeColumnData());

        assertThat(mapper.apply(rs, 1)).isEqualTo(new Range<>(1, 10, true, false));
    }

    @Test
    void readsUnboundedLower() throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        when(rs.getString(1)).thenReturn("(,10]");
        when(rs.wasNull()).thenReturn(false);
        var mapper = module.rangeJdbcResultColumnMapper(module.int4RangeColumnData());

        assertThat(mapper.apply(rs, 1)).isEqualTo(new Range<>(null, 10, false, true));
    }

    @Test
    void readsNull() throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        when(rs.getString(1)).thenReturn(null);
        when(rs.wasNull()).thenReturn(true);
        var mapper = module.rangeJdbcResultColumnMapper(module.int4RangeColumnData());

        assertThat(mapper.apply(rs, 1)).isNull();
    }

    @Test
    void writesOpenClosedRange() throws SQLException {
        var stmt = Mockito.mock(PreparedStatement.class);
        var mapper = module.rangeJdbcParameterColumnMapper(module.int4RangeColumnData());

        mapper.set(stmt, 1, Range.openClosed(1, 10));

        var captor = ArgumentCaptor.forClass(PGobject.class);
        verify(stmt).setObject(eq(1), captor.capture());
        assertThat(captor.getValue().getValue()).isEqualTo("(\"1\",\"10\"]");
    }

    @Test
    void readsEmpty() throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        when(rs.getString(1)).thenReturn("empty");
        when(rs.wasNull()).thenReturn(false);
        var mapper = module.rangeJdbcResultColumnMapper(module.int4RangeColumnData());

        assertThat(mapper.apply(rs, 1)).isEqualTo(new Range<>(null, null, false, false));
    }

    @Test
    void readsQuotedBounds() throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        when(rs.getString(1)).thenReturn("[\"1\",\"10\")");
        when(rs.wasNull()).thenReturn(false);
        var mapper = module.rangeJdbcResultColumnMapper(module.int4RangeColumnData());

        assertThat(mapper.apply(rs, 1)).isEqualTo(new Range<>(1, 10, true, false));
    }
}
