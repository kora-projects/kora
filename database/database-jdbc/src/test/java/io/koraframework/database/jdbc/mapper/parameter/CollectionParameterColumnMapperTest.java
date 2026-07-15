package io.koraframework.database.jdbc.mapper.parameter;

import io.koraframework.database.jdbc.ArrayColumnData;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionParameterColumnMapperTest {

    @Test
    void setsSqlArray() throws SQLException {
        var conn = Mockito.mock(Connection.class);
        var stmt = Mockito.mock(PreparedStatement.class);
        var array = Mockito.mock(Array.class);
        when(stmt.getConnection()).thenReturn(conn);
        when(conn.createArrayOf(eq("varchar"), any())).thenReturn(array);

        var mapper = new CollectionParameterColumnMapper<String, List<String>>(ArrayColumnData.withNoopMapping("varchar"));
        mapper.set(stmt, 1, List.of("a", "b"));

        verify(conn).createArrayOf("varchar", new Object[]{"a", "b"});
        verify(stmt).setArray(1, array);
    }

    @Test
    void setsNullAsSqlArray() throws SQLException {
        var stmt = Mockito.mock(PreparedStatement.class);

        var mapper = new CollectionParameterColumnMapper<String, List<String>>(ArrayColumnData.withNoopMapping("varchar"));
        mapper.set(stmt, 1, null);

        verify(stmt).setNull(1, Types.ARRAY);
    }
}
