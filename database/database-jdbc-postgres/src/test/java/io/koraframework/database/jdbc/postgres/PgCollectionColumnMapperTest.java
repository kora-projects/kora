package io.koraframework.database.jdbc.postgres;

import io.koraframework.database.jdbc.postgres.mapper.parameter.PgCollectionParameterColumnMapper;
import io.koraframework.database.jdbc.postgres.mapper.result.PgCollectionResultColumnMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PgCollectionColumnMapperTest {

    private final Connection connection = Mockito.mock(Connection.class);
    private final PreparedStatement stmt = Mockito.mock(PreparedStatement.class);

    private Object[] writtenElements() throws SQLException {
        var elements = ArgumentCaptor.forClass(Object[].class);
        verify(connection).createArrayOf(anyString(), elements.capture());
        return elements.getValue();
    }

    private void mockConnection() throws SQLException {
        when(stmt.getConnection()).thenReturn(connection);
        when(connection.createArrayOf(anyString(), any())).thenReturn(Mockito.mock(Array.class));
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
    void writesListAsArrayOfDeclaredElementType() throws SQLException {
        mockConnection();
        var array = Mockito.mock(Array.class);
        when(connection.createArrayOf(anyString(), any())).thenReturn(array);
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();

        new PgCollectionParameterColumnMapper<UUID, List<UUID>>("uuid").set(stmt, 1, List.of(first, second));

        var elements = ArgumentCaptor.forClass(Object[].class);
        verify(connection).createArrayOf(eq("uuid"), elements.capture());
        assertThat(elements.getValue()).containsExactly(first, second);
        verify(stmt).setArray(1, array);
    }

    @Test
    void writesSetPreservingIterationOrder() throws SQLException {
        mockConnection();
        Set<String> value = new LinkedHashSet<>(List.of("b", "a", "c"));

        new PgCollectionParameterColumnMapper<String, Set<String>>("varchar").set(stmt, 1, value);

        assertThat(writtenElements()).containsExactly("b", "a", "c");
    }

    @Test
    void writesArbitraryCollection() throws SQLException {
        mockConnection();
        Collection<Integer> value = Arrays.asList(1, 2, 3);

        new PgCollectionParameterColumnMapper<Integer, Collection<Integer>>("int4").set(stmt, 1, value);

        assertThat(writtenElements()).containsExactly(1, 2, 3);
    }

    @Test
    void writesNullElementsAsIs() throws SQLException {
        mockConnection();

        new PgCollectionParameterColumnMapper<String, List<String>>("varchar")
            .set(stmt, 1, Arrays.asList("a", null, "b"));

        assertThat(writtenElements()).containsExactly("a", null, "b");
    }

    @Test
    void writesNullElementsOfSetAsIs() throws SQLException {
        mockConnection();
        Set<String> value = new LinkedHashSet<>(Arrays.asList("a", null, "b"));

        new PgCollectionParameterColumnMapper<String, Set<String>>("varchar").set(stmt, 1, value);

        assertThat(writtenElements()).containsExactly("a", null, "b");
    }

    @Test
    void writesElementsThroughElementWriter() throws SQLException {
        mockConnection();

        new PgCollectionParameterColumnMapper<Integer, List<Integer>>("varchar", String::valueOf)
            .set(stmt, 1, List.of(1, 2));

        assertThat(writtenElements()).containsExactly("1", "2");
    }

    @Test
    void writesNullAsSqlNull() throws SQLException {
        new PgCollectionParameterColumnMapper<UUID, List<UUID>>("uuid").set(stmt, 1, null);

        verify(stmt).setNull(1, Types.ARRAY);
    }

    @Test
    void readsArrayAsList() throws SQLException {
        var row = resultSetWith(1, 2, 3);

        assertThat(new PgCollectionResultColumnMapper<Integer>().apply(row, 1)).containsExactly(1, 2, 3);
    }

    @Test
    void readsNullElementsAsIs() throws SQLException {
        var row = resultSetWith("a", null, "b");

        assertThat(new PgCollectionResultColumnMapper<String>().apply(row, 1)).containsExactly("a", null, "b");
    }

    @Test
    void readsElementsThroughElementReader() throws SQLException {
        var row = resultSetWith("1", "2");
        var mapper = new PgCollectionResultColumnMapper<Integer>(element -> Integer.valueOf((String) element));

        assertThat(mapper.apply(row, 1)).containsExactly(1, 2);
    }

    @Test
    void readsSqlNullAsNull() throws SQLException {
        var row = Mockito.mock(ResultSet.class);
        when(row.getArray(1)).thenReturn(null);
        when(row.wasNull()).thenReturn(true);

        assertThat(new PgCollectionResultColumnMapper<UUID>().apply(row, 1)).isNull();
    }
}
