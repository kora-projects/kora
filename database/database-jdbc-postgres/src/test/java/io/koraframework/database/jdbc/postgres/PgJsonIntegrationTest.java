package io.koraframework.database.jdbc.postgres;

import io.koraframework.json.common.JsonReader;
import io.koraframework.json.common.JsonWriter;
import io.koraframework.test.postgres.PostgresParams;
import io.koraframework.test.postgres.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(PostgresTestContainer.class)
class PgJsonIntegrationTest {

    record Payload(String name) {}

    private static final Payload PAYLOAD = new Payload("kora");
    private static final String PAYLOAD_JSON = "{\"name\":\"kora\"}";

    private final PostgresJdbcDatabaseModule module = new PostgresJdbcDatabaseModule() {};

    @SuppressWarnings("unchecked")
    private static JsonWriter<Payload> writer() {
        var writer = (JsonWriter<Payload>) Mockito.mock(JsonWriter.class);
        when(writer.toString(PAYLOAD)).thenReturn(PAYLOAD_JSON);
        return writer;
    }

    @SuppressWarnings("unchecked")
    private static JsonReader<Payload> reader() {
        var reader = (JsonReader<Payload>) Mockito.mock(JsonReader.class);
        when(reader.read(anyString())).thenReturn(PAYLOAD);
        return reader;
    }

    @Test
    void jsonAndJsonbRoundTrip(PostgresParams params) throws SQLException {
        try (var connection = params.createConnection()) {
            connection.createStatement().execute("CREATE TABLE t (id int, c_json json, c_jsonb jsonb)");

            try (var stmt = connection.prepareStatement("INSERT INTO t VALUES (?, ?, ?)")) {
                stmt.setInt(1, 1);
                module.jsonJdbcParameterColumnMapper(writer()).set(stmt, 2, PAYLOAD);
                module.jsonbJdbcParameterColumnMapper(writer()).set(stmt, 3, PAYLOAD);
                stmt.executeUpdate();

                stmt.setInt(1, 2);
                module.jsonJdbcParameterColumnMapper(writer()).set(stmt, 2, null);
                module.jsonbJdbcParameterColumnMapper(writer()).set(stmt, 3, null);
                stmt.executeUpdate();
            }

            try (var stmt = connection.prepareStatement("SELECT c_json, c_jsonb FROM t ORDER BY id");
                 var rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(module.jsonJdbcResultColumnMapper(reader()).apply(rs, 1)).isEqualTo(PAYLOAD);
                assertThat(module.jsonbJdbcResultColumnMapper(reader()).apply(rs, 2)).isEqualTo(PAYLOAD);
                assertThat(rs.next()).isTrue();
                assertThat(module.jsonJdbcResultColumnMapper(reader()).apply(rs, 1)).isNull();
                assertThat(module.jsonbJdbcResultColumnMapper(reader()).apply(rs, 2)).isNull();
            }
        }
    }

    @Test
    void jsonbContainmentQuery(PostgresParams params) throws SQLException {
        try (var connection = params.createConnection()) {
            connection.createStatement().execute("CREATE TABLE t (c_jsonb jsonb)");
            connection.createStatement().execute("INSERT INTO t VALUES ('{\"name\": \"kora\", \"version\": 2}')");

            try (var stmt = connection.prepareStatement("SELECT count(*) FROM t WHERE c_jsonb @> ?")) {
                module.jsonbJdbcParameterColumnMapper(writer()).set(stmt, 1, PAYLOAD);
                try (var rs = stmt.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }
        }
    }
}
