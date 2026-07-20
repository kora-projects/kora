package io.koraframework.database.jdbc.postgres;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.postgresql.util.PGInterval;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.Period;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PgIntervalJdbcMappersModuleTest {

    private final PgIntervalJdbcMappersModule module = new PgIntervalJdbcMappersModule() {};

    @Test
    void writesDurationAsPgInterval() throws SQLException {
        var stmt = Mockito.mock(PreparedStatement.class);
        var duration = Duration.ofDays(2).plusHours(3).plusMinutes(4).plusSeconds(5);

        module.durationJdbcParameterColumnMapper().set(stmt, 1, duration);

        var captor = ArgumentCaptor.forClass(PGInterval.class);
        verify(stmt).setObject(eq(1), captor.capture());
        var pg = captor.getValue();
        assertThat(pg.getDays()).isEqualTo(2);
        assertThat(pg.getHours()).isEqualTo(3);
        assertThat(pg.getMinutes()).isEqualTo(4);
        assertThat((int) pg.getSeconds()).isEqualTo(5);
    }

    @Test
    void writesNull() throws SQLException {
        var stmt = Mockito.mock(PreparedStatement.class);

        module.durationJdbcParameterColumnMapper().set(stmt, 1, null);

        verify(stmt).setNull(1, Types.OTHER);
    }

    @Test
    void readsPgIntervalAsDuration() throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        when(rs.getObject(1, PGInterval.class)).thenReturn(new PGInterval(0, 0, 2, 3, 4, 5));
        when(rs.wasNull()).thenReturn(false);

        var result = module.durationJdbcResultColumnMapper().apply(rs, 1);

        assertThat(result).isEqualTo(Duration.ofDays(2).plusHours(3).plusMinutes(4).plusSeconds(5));
    }

    @Test
    void readsNull() throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        when(rs.getObject(1, PGInterval.class)).thenReturn(null);
        when(rs.wasNull()).thenReturn(true);

        assertThat(module.durationJdbcResultColumnMapper().apply(rs, 1)).isNull();
    }

    @Test
    void writesPeriodAsPgInterval() throws SQLException {
        var stmt = Mockito.mock(PreparedStatement.class);
        var period = Period.of(1, 2, 3);

        module.periodJdbcParameterColumnMapper().set(stmt, 1, period);

        var captor = ArgumentCaptor.forClass(PGInterval.class);
        verify(stmt).setObject(eq(1), captor.capture());
        var pg = captor.getValue();
        assertThat(pg.getYears()).isEqualTo(1);
        assertThat(pg.getMonths()).isEqualTo(2);
        assertThat(pg.getDays()).isEqualTo(3);
    }

    @Test
    void writesNullPeriod() throws SQLException {
        var stmt = Mockito.mock(PreparedStatement.class);

        module.periodJdbcParameterColumnMapper().set(stmt, 1, null);

        verify(stmt).setNull(1, Types.OTHER);
    }

    @Test
    void readsPgIntervalAsPeriod() throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        when(rs.getObject(1, PGInterval.class)).thenReturn(new PGInterval(1, 2, 3, 0, 0, 0));
        when(rs.wasNull()).thenReturn(false);

        var result = module.periodJdbcResultColumnMapper().apply(rs, 1);

        assertThat(result).isEqualTo(Period.of(1, 2, 3));
    }

    @Test
    void readsNullPeriod() throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        when(rs.getObject(1, PGInterval.class)).thenReturn(null);
        when(rs.wasNull()).thenReturn(true);

        assertThat(module.periodJdbcResultColumnMapper().apply(rs, 1)).isNull();
    }

    @Test
    void durationRowMapperDelegatesToResult() throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        when(rs.getObject(1, PGInterval.class)).thenReturn(new PGInterval(0, 0, 2, 3, 4, 5));
        when(rs.wasNull()).thenReturn(false);

        var rowMapper = module.durationJdbcRowMapper(module.durationJdbcResultColumnMapper());

        assertThat(rowMapper.apply(rs)).isEqualTo(Duration.ofDays(2).plusHours(3).plusMinutes(4).plusSeconds(5));
    }

    @Test
    void periodRowMapperDelegatesToResult() throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        when(rs.getObject(1, PGInterval.class)).thenReturn(new PGInterval(1, 2, 3, 0, 0, 0));
        when(rs.wasNull()).thenReturn(false);

        var rowMapper = module.periodJdbcRowMapper(module.periodJdbcResultColumnMapper());

        assertThat(rowMapper.apply(rs)).isEqualTo(Period.of(1, 2, 3));
    }
}
