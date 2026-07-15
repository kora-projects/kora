package io.koraframework.database.jdbc.postgres.mapper.parameter;

import io.koraframework.database.jdbc.EnumColumnData;
import io.koraframework.database.jdbc.PrimitiveJdbcMappersModule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.postgresql.util.PGobject;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class PostgresEnumJdbcParameterColumnMapperTest {

    enum Status { ACTIVE, INACTIVE }

    private final PrimitiveJdbcMappersModule primitives = new PrimitiveJdbcMappersModule() {};

    @Test
    void writesPgObjectForNativeEnum() throws SQLException {
        var stmt = Mockito.mock(PreparedStatement.class);
        var data = EnumColumnData.byName(Status.class).withSqlTypeName("status_enum");
        var mapper = new PostgresEnumJdbcParameterColumnMapper<>(data, primitives.stringJdbcParameterColumnMapper());

        mapper.set(stmt, 1, Status.ACTIVE);

        var captor = ArgumentCaptor.forClass(PGobject.class);
        verify(stmt).setObject(eq(1), captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("status_enum");
        assertThat(captor.getValue().getValue()).isEqualTo("ACTIVE");
    }

    @Test
    void delegatesToValueMapperWhenNoSqlTypeName() throws SQLException {
        var stmt = Mockito.mock(PreparedStatement.class);
        var data = EnumColumnData.byName(Status.class);
        var mapper = new PostgresEnumJdbcParameterColumnMapper<>(data, primitives.stringJdbcParameterColumnMapper());

        mapper.set(stmt, 1, Status.ACTIVE);

        verify(stmt).setString(1, "ACTIVE");
    }

    @Test
    void writesNullPgObjectForNativeEnum() throws SQLException {
        var stmt = Mockito.mock(PreparedStatement.class);
        var data = EnumColumnData.byName(Status.class).withSqlTypeName("status_enum");
        var mapper = new PostgresEnumJdbcParameterColumnMapper<>(data, primitives.stringJdbcParameterColumnMapper());

        mapper.set(stmt, 1, null);

        var captor = ArgumentCaptor.forClass(PGobject.class);
        verify(stmt).setObject(eq(1), captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("status_enum");
        assertThat(captor.getValue().getValue()).isNull();
    }

    @Test
    void delegatesNullToValueMapperWhenNoSqlTypeName() throws SQLException {
        var stmt = Mockito.mock(PreparedStatement.class);
        var data = EnumColumnData.byName(Status.class);
        var mapper = new PostgresEnumJdbcParameterColumnMapper<>(data, primitives.stringJdbcParameterColumnMapper());

        mapper.set(stmt, 1, null);

        verify(stmt).setNull(1, Types.VARCHAR);
    }
}
