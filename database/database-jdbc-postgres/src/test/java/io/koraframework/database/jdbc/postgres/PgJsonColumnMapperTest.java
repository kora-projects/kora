package io.koraframework.database.jdbc.postgres;

import io.koraframework.database.jdbc.postgres.mapper.parameter.PgJsonParameterColumnMapper;
import io.koraframework.database.jdbc.postgres.mapper.result.PgJsonResultColumnMapper;
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

class PgJsonColumnMapperTest {

    record Payload(String name) {}

    private static final Payload PAYLOAD = new Payload("kora");
    private static final String PAYLOAD_JSON = "{\"name\":\"kora\"}";

    @SuppressWarnings("unchecked")
    private static JsonWriter<Payload> writer() {
        var writer = (JsonWriter<Payload>) Mockito.mock(JsonWriter.class);
        when(writer.toString(PAYLOAD)).thenReturn(PAYLOAD_JSON);
        return writer;
    }

    @SuppressWarnings("unchecked")
    private static JsonReader<Payload> reader() {
        var reader = (JsonReader<Payload>) Mockito.mock(JsonReader.class);
        when(reader.read(PAYLOAD_JSON)).thenReturn(PAYLOAD);
        return reader;
    }

    @Test
    void writesJsonTypedParameter() throws SQLException {
        var stmt = Mockito.mock(PreparedStatement.class);

        new PgJsonParameterColumnMapper<>(writer(), "json").set(stmt, 1, PAYLOAD);

        var captor = ArgumentCaptor.forClass(PGobject.class);
        verify(stmt).setObject(eq(1), captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("json");
        assertThat(captor.getValue().getValue()).isEqualTo(PAYLOAD_JSON);
    }

    @Test
    void writesJsonbTypedParameter() throws SQLException {
        var stmt = Mockito.mock(PreparedStatement.class);

        new PgJsonParameterColumnMapper<>(writer(), "jsonb").set(stmt, 1, PAYLOAD);

        var captor = ArgumentCaptor.forClass(PGobject.class);
        verify(stmt).setObject(eq(1), captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("jsonb");
    }

    @Test
    void writesNullAsSqlNull() throws SQLException {
        var stmt = Mockito.mock(PreparedStatement.class);

        new PgJsonParameterColumnMapper<>(writer(), "jsonb").set(stmt, 1, null);

        verify(stmt).setNull(1, Types.OTHER);
    }

    @Test
    void readsJson() throws SQLException {
        var row = Mockito.mock(ResultSet.class);
        when(row.getString(1)).thenReturn(PAYLOAD_JSON);
        when(row.wasNull()).thenReturn(false);

        assertThat(new PgJsonResultColumnMapper<>(reader()).apply(row, 1)).isEqualTo(PAYLOAD);
    }

    @Test
    void readsSqlNullAsNull() throws SQLException {
        var row = Mockito.mock(ResultSet.class);
        when(row.getString(1)).thenReturn(null);
        when(row.wasNull()).thenReturn(true);

        assertThat(new PgJsonResultColumnMapper<>(reader()).apply(row, 1)).isNull();
    }
}
