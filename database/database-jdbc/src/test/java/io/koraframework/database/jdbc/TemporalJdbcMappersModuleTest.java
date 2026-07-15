package io.koraframework.database.jdbc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemporalJdbcMappersModuleTest {

    private final TemporalJdbcMappersModule module = new TemporalJdbcMappersModule() {};

    @Test
    void instantResultColumn() throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        var instant = Instant.parse("2020-01-02T03:04:05Z");
        when(rs.getObject(1, OffsetDateTime.class)).thenReturn(instant.atOffset(ZoneOffset.UTC));

        assertThat(module.instantJdbcResultColumnMapper().apply(rs, 1)).isEqualTo(instant);
    }

    @Test
    void instantResultColumnNull() throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        when(rs.getObject(1, OffsetDateTime.class)).thenReturn(null);
        when(rs.wasNull()).thenReturn(true);

        assertThat(module.instantJdbcResultColumnMapper().apply(rs, 1)).isNull();
    }

    @Test
    void instantRow() throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        var instant = Instant.parse("2020-01-02T03:04:05Z");
        when(rs.getObject(1, OffsetDateTime.class)).thenReturn(instant.atOffset(ZoneOffset.UTC));

        assertThat(module.instantJdbcRowMapper().apply(rs)).isEqualTo(instant);
    }

    @Test
    void instantParameter() throws SQLException {
        var stmt = Mockito.mock(PreparedStatement.class);
        var instant = Instant.parse("2020-01-02T03:04:05Z");

        module.instantJdbcParameterColumnMapper().set(stmt, 1, instant);

        verify(stmt).setObject(1, instant.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE);
    }

    @Test
    void instantParameterNull() throws SQLException {
        var stmt = Mockito.mock(PreparedStatement.class);

        module.instantJdbcParameterColumnMapper().set(stmt, 1, null);

        verify(stmt).setNull(1, Types.TIMESTAMP_WITH_TIMEZONE);
    }
}
