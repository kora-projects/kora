package io.koraframework.database.jdbc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnumJdbcMappersModuleTest {

    enum Status { ACTIVE, INACTIVE }

    private final PrimitiveJdbcMappersModule primitives = new PrimitiveJdbcMappersModule() {};
    private final EnumJdbcMappersModule module = new EnumJdbcMappersModule() {};

    @Test
    void parameterFactoryDelegatesToValueMapper() throws SQLException {
        var stmt = Mockito.mock(PreparedStatement.class);
        var mapper = module.enumJdbcParameterColumnMapper(
                EnumColumnData.byName(Status.class), primitives.stringJdbcParameterColumnMapper());

        mapper.set(stmt, 1, Status.ACTIVE);

        verify(stmt).setString(1, "ACTIVE");
    }

    @Test
    void resultFactoryDelegatesToValueMapper() throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        when(rs.getString(1)).thenReturn("INACTIVE");
        when(rs.wasNull()).thenReturn(false);
        var mapper = module.enumJdbcResultColumnMapper(
                EnumColumnData.byName(Status.class), primitives.stringJdbcResultColumnMapper());

        assertThat(mapper.apply(rs, 1)).isEqualTo(Status.INACTIVE);
    }

    @Test
    void rowFactoryReadsFirstColumn() throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        when(rs.getString(1)).thenReturn("ACTIVE");
        when(rs.wasNull()).thenReturn(false);
        var columnMapper = module.enumJdbcResultColumnMapper(
                EnumColumnData.byName(Status.class), primitives.stringJdbcResultColumnMapper());
        var rowMapper = module.enumJdbcRowMapper(columnMapper);

        assertThat(rowMapper.apply(rs)).isEqualTo(Status.ACTIVE);
    }
}
