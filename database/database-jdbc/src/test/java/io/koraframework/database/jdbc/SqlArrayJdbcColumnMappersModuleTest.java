package io.koraframework.database.jdbc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqlArrayJdbcColumnMappersModuleTest {

    private final SqlArrayJdbcColumnMappersModule module = new SqlArrayJdbcColumnMappersModule() {};

    @Test
    void listParameterMapperWritesSqlArray() throws SQLException {
        var conn = Mockito.mock(Connection.class);
        var stmt = Mockito.mock(PreparedStatement.class);
        var array = Mockito.mock(Array.class);
        when(stmt.getConnection()).thenReturn(conn);
        when(conn.createArrayOf(eq("varchar"), any())).thenReturn(array);

        var mapper = module.listJdbcParameterColumnMapper(ArrayColumnData.withNoopMapping("varchar"));
        mapper.set(stmt, 1, List.of("a", "b"));

        verify(conn).createArrayOf("varchar", new Object[]{"a", "b"});
        verify(stmt).setArray(1, array);
    }

    @Test
    void setResultMapperReadsSqlArray() throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        var array = Mockito.mock(Array.class);
        when(rs.getArray(1)).thenReturn(array);
        when(rs.wasNull()).thenReturn(false);
        when(array.getArray()).thenReturn(new Object[]{"a", "b", "a"});

        var mapper = module.setJdbcResultColumnMapper(ArrayColumnData.withNoopMapping("varchar"));

        assertThat(mapper.apply(rs, 1)).containsExactlyInAnyOrder("a", "b");
    }
}
