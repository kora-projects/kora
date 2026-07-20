package io.koraframework.database.jdbc.mapper.result;

import io.koraframework.database.jdbc.EnumColumnData;
import io.koraframework.database.jdbc.PrimitiveJdbcMappersModule;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class EnumJdbcResultColumnMapperTest {

    enum Status { ACTIVE, INACTIVE }

    private final PrimitiveJdbcMappersModule primitives = new PrimitiveJdbcMappersModule() {};

    @Test
    void readsEnumFromString() throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        when(rs.getString(1)).thenReturn("INACTIVE");
        when(rs.wasNull()).thenReturn(false);

        var mapper = new EnumJdbcResultColumnMapper<>(
                EnumColumnData.byName(Status.class), primitives.stringJdbcResultColumnMapper());

        assertThat(mapper.apply(rs, 1)).isEqualTo(Status.INACTIVE);
    }

    @Test
    void readsEnumFromOrdinal() throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        when(rs.getInt(1)).thenReturn(0);
        when(rs.wasNull()).thenReturn(false);

        var mapper = new EnumJdbcResultColumnMapper<>(
                EnumColumnData.byOrdinal(Status.class), primitives.integerJdbcResultColumnMapper());

        assertThat(mapper.apply(rs, 1)).isEqualTo(Status.ACTIVE);
    }

    @Test
    void readsNull() throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        when(rs.getString(1)).thenReturn(null);
        when(rs.wasNull()).thenReturn(true);

        var mapper = new EnumJdbcResultColumnMapper<>(
                EnumColumnData.byName(Status.class), primitives.stringJdbcResultColumnMapper());

        assertThat(mapper.apply(rs, 1)).isNull();
    }
}
