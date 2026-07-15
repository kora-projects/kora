package io.koraframework.database.jdbc.mapper.result;

import io.koraframework.database.jdbc.ArrayColumnData;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class CollectionFromSqlArrayResultColumnMapperTest {

    @Test
    void readsListFromSqlArray() throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        var array = Mockito.mock(Array.class);
        when(rs.getArray(1)).thenReturn(array);
        when(rs.wasNull()).thenReturn(false);
        when(array.getArray()).thenReturn(new Object[]{"a", "b"});

        var mapper = new CollectionFromSqlArrayResultColumnMapper<String, List<String>>(
                ArrayColumnData.withNoopMapping("varchar"), new ListCollectionFactory<>());

        assertThat(mapper.apply(rs, 1)).containsExactly("a", "b");
    }

    @Test
    void readsNull() throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        when(rs.getArray(1)).thenReturn(null);
        when(rs.wasNull()).thenReturn(true);

        var mapper = new CollectionFromSqlArrayResultColumnMapper<String, List<String>>(
                ArrayColumnData.withNoopMapping("varchar"), new ListCollectionFactory<>());

        assertThat(mapper.apply(rs, 1)).isNull();
    }
}
