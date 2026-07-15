package io.koraframework.database.jdbc.postgres;

import io.koraframework.database.jdbc.EnumColumnData;
import io.koraframework.database.jdbc.PrimitiveJdbcMappersModule;
import io.koraframework.database.jdbc.postgres.mapper.parameter.PostgresEnumJdbcParameterColumnMapper;
import io.koraframework.test.postgres.PostgresParams;
import io.koraframework.test.postgres.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(PostgresTestContainer.class)
class PostgresEnumIntegrationTest {

    enum Status {ACTIVE, INACTIVE}

    private PostgresEnumJdbcParameterColumnMapper<Status, String> nativeEnumMapper() {
        var enumData = EnumColumnData.byName(Status.class).withSqlTypeName("mood");
        var valueMapper = new PrimitiveJdbcMappersModule() {}.stringJdbcParameterColumnMapper();
        return new PostgresEnumJdbcParameterColumnMapper<>(enumData, valueMapper);
    }

    @Test
    void nativeEnumNonNullRoundTrip(PostgresParams params) throws SQLException {
        try (var conn = params.createConnection()) {
            conn.createStatement().execute("CREATE TYPE mood AS ENUM ('ACTIVE', 'INACTIVE')");
            conn.createStatement().execute("CREATE TABLE t (id int, s mood)");

            try (var ps = conn.prepareStatement("INSERT INTO t(id, s) VALUES (1, ?)")) {
                nativeEnumMapper().set(ps, 1, Status.ACTIVE);
                ps.executeUpdate();
            }
            try (var ps = conn.prepareStatement("SELECT s FROM t WHERE id = 1");
                 var rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getString(1)).isEqualTo("ACTIVE");
            }
        }
    }

    @Test
    void nativeEnumNullInsertsSqlNull(PostgresParams params) throws SQLException {
        try (var conn = params.createConnection()) {
            conn.createStatement().execute("CREATE TYPE mood AS ENUM ('ACTIVE', 'INACTIVE')");
            conn.createStatement().execute("CREATE TABLE t (id int, s mood)");

            try (var ps = conn.prepareStatement("INSERT INTO t(id, s) VALUES (1, ?)")) {
                nativeEnumMapper().set(ps, 1, null);
                ps.executeUpdate();
            }
            try (var ps = conn.prepareStatement("SELECT s FROM t WHERE id = 1");
                 var rs = ps.executeQuery()) {
                rs.next();
                var s = rs.getString(1);
                assertThat(rs.wasNull()).isTrue();
                assertThat(s).isNull();
            }
        }
    }
}
