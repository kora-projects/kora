package io.koraframework.database.jdbc.postgres;

import io.koraframework.database.jdbc.postgres.mapper.parameter.PgArrayParameterColumnMapper;
import io.koraframework.database.jdbc.postgres.mapper.result.PgArrayResultColumnMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PgArrayColumnMapperTest {

    private final Connection connection = Mockito.mock(Connection.class);
    private final PreparedStatement stmt = Mockito.mock(PreparedStatement.class);

    private void mockConnection() throws SQLException {
        when(stmt.getConnection()).thenReturn(connection);
        when(connection.createArrayOf(anyString(), any())).thenReturn(Mockito.mock(Array.class));
    }

    private Object[] writtenElements() throws SQLException {
        var elements = ArgumentCaptor.forClass(Object[].class);
        verify(connection).createArrayOf(anyString(), elements.capture());
        return elements.getValue();
    }

    private static ResultSet resultSetWith(Object... elements) throws SQLException {
        var row = Mockito.mock(ResultSet.class);
        var array = Mockito.mock(Array.class);
        when(row.getArray(1)).thenReturn(array);
        when(row.wasNull()).thenReturn(false);
        when(array.getArray()).thenReturn(elements);
        return row;
    }

    @Test
    void writesArrayOfDeclaredElementType() throws SQLException {
        mockConnection();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();

        new PgArrayParameterColumnMapper<UUID>("uuid").set(stmt, 1, new UUID[]{first, second});

        var elements = ArgumentCaptor.forClass(Object[].class);
        verify(connection).createArrayOf(eq("uuid"), elements.capture());
        assertThat(elements.getValue()).containsExactly(first, second);
    }

    @Test
    void writesNullElementsAsIs() throws SQLException {
        mockConnection();

        new PgArrayParameterColumnMapper<String>("varchar").set(stmt, 1, new String[]{"a", null, "b"});

        assertThat(writtenElements()).containsExactly("a", null, "b");
    }

    @Test
    void writesElementsThroughElementWriter() throws SQLException {
        mockConnection();

        new PgArrayParameterColumnMapper<Integer>("varchar", String::valueOf).set(stmt, 1, new Integer[]{1, 2});

        assertThat(writtenElements()).containsExactly("1", "2");
    }

    @Test
    void writesNullAsSqlNull() throws SQLException {
        new PgArrayParameterColumnMapper<UUID>("uuid").set(stmt, 1, null);

        verify(stmt).setNull(1, Types.ARRAY);
    }

    @Test
    void readsArrayOfDeclaredComponentType() throws SQLException {
        var row = resultSetWith(1, 2, 3);

        var result = new PgArrayResultColumnMapper<>(Integer[]::new).apply(row, 1);

        assertThat(result).isInstanceOf(Integer[].class).containsExactly(1, 2, 3);
    }

    @Test
    void readsNullElementsAsIs() throws SQLException {
        var row = resultSetWith("a", null, "b");

        assertThat(new PgArrayResultColumnMapper<>(String[]::new).apply(row, 1)).containsExactly("a", null, "b");
    }

    @Test
    void readsElementsThroughElementReader() throws SQLException {
        var row = resultSetWith("1", "2");
        var mapper = new PgArrayResultColumnMapper<>(Integer[]::new, element -> Integer.valueOf((String) element));

        assertThat(mapper.apply(row, 1)).containsExactly(1, 2);
    }

    @Test
    void readsSqlNullAsNull() throws SQLException {
        var row = Mockito.mock(ResultSet.class);
        when(row.getArray(1)).thenReturn(null);
        when(row.wasNull()).thenReturn(true);

        assertThat(new PgArrayResultColumnMapper<>(UUID[]::new).apply(row, 1)).isNull();
    }
}
