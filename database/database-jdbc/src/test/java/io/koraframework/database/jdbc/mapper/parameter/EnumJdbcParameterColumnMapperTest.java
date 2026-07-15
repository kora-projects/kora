package io.koraframework.database.jdbc.mapper.parameter;

import io.koraframework.database.jdbc.EnumColumnData;
import io.koraframework.database.jdbc.PrimitiveJdbcMappersModule;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

import static org.mockito.Mockito.verify;

class EnumJdbcParameterColumnMapperTest {

    enum Status { ACTIVE, INACTIVE }

    private final PrimitiveJdbcMappersModule primitives = new PrimitiveJdbcMappersModule() {};

    @Test
    void writesNameAsStringViaValueMapper() throws SQLException {
        var stmt = Mockito.mock(PreparedStatement.class);
        var mapper = new EnumJdbcParameterColumnMapper<>(
                EnumColumnData.byName(Status.class), primitives.stringJdbcParameterColumnMapper());

        mapper.set(stmt, 1, Status.ACTIVE);

        verify(stmt).setString(1, "ACTIVE");
    }

    @Test
    void writesOrdinalAsIntViaValueMapper() throws SQLException {
        var stmt = Mockito.mock(PreparedStatement.class);
        var mapper = new EnumJdbcParameterColumnMapper<>(
                EnumColumnData.byOrdinal(Status.class), primitives.integerJdbcParameterColumnMapper());

        mapper.set(stmt, 1, Status.INACTIVE);

        verify(stmt).setInt(1, 1);
    }

    @Test
    void writesNullDelegatesToValueMapper() throws SQLException {
        var stmt = Mockito.mock(PreparedStatement.class);
        var mapper = new EnumJdbcParameterColumnMapper<>(
                EnumColumnData.byName(Status.class), primitives.stringJdbcParameterColumnMapper());

        mapper.set(stmt, 1, null);

        verify(stmt).setNull(1, Types.VARCHAR);
    }
}
