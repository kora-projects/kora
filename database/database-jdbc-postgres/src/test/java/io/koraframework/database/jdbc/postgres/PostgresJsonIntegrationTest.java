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
class PostgresJsonIntegrationTest {

    record Payload(String name) {}

    private final PostgresJsonJdbcColumnMappersModule module = new PostgresJsonJdbcColumnMappersModule() {};

    @Test
    @SuppressWarnings("unchecked")
    void jsonbRoundTripViaPgObject(PostgresParams params) throws SQLException {
        try (var conn = params.createConnection()) {
            conn.createStatement().execute("CREATE TABLE t (id int, payload jsonb)");
            var writer = (JsonWriter<Payload>) Mockito.mock(JsonWriter.class);
            var reader = (JsonReader<Payload>) Mockito.mock(JsonReader.class);
            var payload = new Payload("x");
            when(writer.toString(payload)).thenReturn("{\"name\":\"x\"}");
            when(reader.read(anyString())).thenReturn(payload);

            var paramMapper = module.jsonColumnJdbcParameterColumnMapper(writer);
            var resultMapper = module.jsonColumnJdbcResultColumnMapper(reader);

            try (var ps = conn.prepareStatement("INSERT INTO t(id, payload) VALUES (1, ?)")) {
                paramMapper.set(ps, 1, payload);
                ps.executeUpdate();
            }
            try (var ps = conn.prepareStatement("SELECT payload FROM t WHERE id = 1");
                 var rs = ps.executeQuery()) {
                rs.next();
                assertThat(resultMapper.apply(rs, 1)).isEqualTo(payload);
            }
        }
    }
}
