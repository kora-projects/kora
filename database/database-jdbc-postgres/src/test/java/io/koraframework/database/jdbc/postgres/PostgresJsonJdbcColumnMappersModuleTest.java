package io.koraframework.database.jdbc.postgres;

import io.koraframework.json.common.JsonReader;
import io.koraframework.json.common.JsonWriter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.postgresql.util.PGobject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgresJsonJdbcColumnMappersModuleTest {

    record Payload(String name) {}

    private final PostgresJsonJdbcColumnMappersModule module = new PostgresJsonJdbcColumnMappersModule() {};

    @Test
    @SuppressWarnings("unchecked")
    void resultMapperReadsJsonViaReader() throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        var reader = (JsonReader<Payload>) Mockito.mock(JsonReader.class);
        var payload = new Payload("x");
        when(rs.getString(1)).thenReturn("{\"name\":\"x\"}");
        when(rs.wasNull()).thenReturn(false);
        when(reader.read("{\"name\":\"x\"}")).thenReturn(payload);

        var mapper = module.jsonColumnJdbcResultColumnMapper(reader);

        assertThat(mapper.apply(rs, 1)).isEqualTo(payload);
    }

    @Test
    @SuppressWarnings("unchecked")
    void resultMapperReadsNull() throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        var reader = (JsonReader<Payload>) Mockito.mock(JsonReader.class);
        when(rs.getString(1)).thenReturn(null);
        when(rs.wasNull()).thenReturn(true);

        var mapper = module.jsonColumnJdbcResultColumnMapper(reader);

        assertThat(mapper.apply(rs, 1)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void parameterMapperWritesPgObjectJson() throws SQLException {
        var stmt = Mockito.mock(PreparedStatement.class);
        var writer = (JsonWriter<Payload>) Mockito.mock(JsonWriter.class);
        var payload = new Payload("x");
        when(writer.toString(payload)).thenReturn("{\"name\":\"x\"}");

        var mapper = module.jsonColumnJdbcParameterColumnMapper(writer);
        mapper.set(stmt, 1, payload);

        var captor = ArgumentCaptor.forClass(PGobject.class);
        verify(stmt).setObject(eq(1), captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("jsonb");
        assertThat(captor.getValue().getValue()).isEqualTo("{\"name\":\"x\"}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void parameterMapperWritesNull() throws SQLException {
        var stmt = Mockito.mock(PreparedStatement.class);
        var writer = (JsonWriter<Payload>) Mockito.mock(JsonWriter.class);

        var mapper = module.jsonColumnJdbcParameterColumnMapper(writer);
        mapper.set(stmt, 1, null);

        verify(stmt).setNull(1, Types.OTHER);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rowMapperDelegatesToResultMapper() throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        var reader = (JsonReader<Payload>) Mockito.mock(JsonReader.class);
        var payload = new Payload("x");
        when(rs.getString(1)).thenReturn("{\"name\":\"x\"}");
        when(rs.wasNull()).thenReturn(false);
        when(reader.read("{\"name\":\"x\"}")).thenReturn(payload);

        var rowMapper = module.jsonColumnJdbcRowMapper(module.jsonColumnJdbcResultColumnMapper(reader));

        assertThat(rowMapper.apply(rs)).isEqualTo(payload);
    }
}
