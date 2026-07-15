package io.koraframework.database.jdbc;

import io.koraframework.database.jdbc.mapper.result.JdbcRowMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class CollectionJdbcResultSetMappersModuleTest {

    private final CollectionJdbcResultSetMappersModule module = new CollectionJdbcResultSetMappersModule() {};

    @Test
    void setResultSet() throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        when(rs.next()).thenReturn(true, true, true, false);
        JdbcRowMapper<String> rowMapper = r -> r.getString(1);
        when(rs.getString(1)).thenReturn("a", "b", "a");

        var result = module.setResultSetMapper(rowMapper).apply(rs);

        assertThat(result).isInstanceOf(Set.class);
        assertThat(result).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void collectionResultSet() throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        when(rs.next()).thenReturn(true, true, false);
        JdbcRowMapper<String> rowMapper = r -> r.getString(1);
        when(rs.getString(1)).thenReturn("x", "y");

        var result = module.collectionResultSetMapper(rowMapper).apply(rs);

        assertThat(result).containsExactly("x", "y");
    }
}
