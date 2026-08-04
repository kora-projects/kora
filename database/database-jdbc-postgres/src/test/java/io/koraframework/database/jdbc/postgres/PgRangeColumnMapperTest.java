package io.koraframework.database.jdbc.postgres;

import io.koraframework.database.jdbc.postgres.mapper.parameter.PgRangeParameterColumnMapper;
import io.koraframework.database.jdbc.postgres.mapper.result.PgRangeResultColumnMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.postgresql.util.PGobject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PgRangeColumnMapperTest {

    private static final PgRangeParameterColumnMapper<Integer> INT4_WRITER =
        new PgRangeParameterColumnMapper<>("int4range", String::valueOf);
    private static final PgRangeResultColumnMapper<Integer> INT4_READER =
        new PgRangeResultColumnMapper<>(Integer::valueOf);

    private static <T> PGobject write(PgRangeParameterColumnMapper<T> mapper, PgRange<T> range) throws SQLException {
        var stmt = Mockito.mock(PreparedStatement.class);
        mapper.set(stmt, 1, range);

        var captor = ArgumentCaptor.forClass(PGobject.class);
        verify(stmt).setObject(eq(1), captor.capture());
        return captor.getValue();
    }

    private static <T> PgRange<T> read(PgRangeResultColumnMapper<T> mapper, String value) throws SQLException {
        var row = Mockito.mock(ResultSet.class);
        when(row.getString(1)).thenReturn(value);
        when(row.wasNull()).thenReturn(false);
        return mapper.apply(row, 1);
    }

    @Test
    void writesClosedOpenRange() throws SQLException {
        var pgObject = write(INT4_WRITER, PgRange.closedOpen(1, 10));

        assertThat(pgObject.getType()).isEqualTo("int4range");
        assertThat(pgObject.getValue()).isEqualTo("[\"1\",\"10\")");
    }

    @Test
    void writesOpenClosedRange() throws SQLException {
        assertThat(write(INT4_WRITER, PgRange.openClosed(1, 10)).getValue()).isEqualTo("(\"1\",\"10\"]");
    }

    @Test
    void writesUnboundedBounds() throws SQLException {
        assertThat(write(INT4_WRITER, PgRange.closedOpen(5, null)).getValue()).isEqualTo("[\"5\",)");
        assertThat(write(INT4_WRITER, PgRange.openClosed(null, 5)).getValue()).isEqualTo("(,\"5\"]");
        assertThat(write(INT4_WRITER, PgRange.open(null, null)).getValue()).isEqualTo("(,)");
    }

    @Test
    void writesEmptyRange() throws SQLException {
        assertThat(write(INT4_WRITER, PgRange.empty()).getValue()).isEqualTo("empty");
    }

    @Test
    void writesEscapedBounds() throws SQLException {
        var mapper = new PgRangeParameterColumnMapper<String>("textrange", Function.identity());

        assertThat(write(mapper, PgRange.closedOpen("a\"b\\c", "z")).getValue()).isEqualTo("[\"a\\\"b\\\\c\",\"z\")");
    }

    @Test
    void writesNullAsSqlNull() throws SQLException {
        var stmt = Mockito.mock(PreparedStatement.class);

        INT4_WRITER.set(stmt, 1, null);

        verify(stmt).setNull(1, Types.OTHER);
    }

    @Test
    void readsClosedOpenRange() throws SQLException {
        assertThat(read(INT4_READER, "[1,10)")).isEqualTo(PgRange.closedOpen(1, 10));
    }

    @Test
    void readsQuotedBounds() throws SQLException {
        assertThat(read(INT4_READER, "[\"1\",\"10\")")).isEqualTo(PgRange.closedOpen(1, 10));
    }

    @Test
    void readsUnboundedBounds() throws SQLException {
        assertThat(read(INT4_READER, "(,10]")).isEqualTo(PgRange.openClosed(null, 10));
        assertThat(read(INT4_READER, "[5,)")).isEqualTo(PgRange.closedOpen(5, null));
        assertThat(read(INT4_READER, "(,)")).isEqualTo(PgRange.open(null, null));
    }

    @Test
    void readsEmptyRangeDistinctFromUnbounded() throws SQLException {
        assertThat(read(INT4_READER, "empty")).isEqualTo(PgRange.empty());
        assertThat(read(INT4_READER, "empty")).isNotEqualTo(read(INT4_READER, "(,)"));
    }

    @Test
    void readsEscapedBounds() throws SQLException {
        var mapper = new PgRangeResultColumnMapper<String>(Function.identity());

        assertThat(read(mapper, "[\"a\\\"b\\\\c\",\"z\")")).isEqualTo(PgRange.closedOpen("a\"b\\c", "z"));
    }

    @Test
    void readsCommaInsideQuotedBound() throws SQLException {
        var mapper = new PgRangeResultColumnMapper<String>(Function.identity());

        assertThat(read(mapper, "[\"a,b\",\"z\")")).isEqualTo(PgRange.closedOpen("a,b", "z"));
    }

    @Test
    void readsSqlNullAsNull() throws SQLException {
        var row = Mockito.mock(ResultSet.class);
        when(row.getString(1)).thenReturn(null);
        when(row.wasNull()).thenReturn(true);

        assertThat(INT4_READER.apply(row, 1)).isNull();
    }

    @Test
    void failsOnIllegalRangeValue() {
        assertThatThrownBy(() -> read(INT4_READER, "[1)")).isInstanceOf(SQLException.class);
    }

    @Test
    void emptyRangeIgnoresBounds() {
        assertThat(new PgRange<>(1, 10, true, true, true)).isEqualTo(PgRange.<Integer>empty());
    }
}
