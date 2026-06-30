package io.koraframework.database.cassandra;

import io.koraframework.database.common.QueryContext;
import io.koraframework.database.common.telemetry.*;
import io.koraframework.database.common.telemetry.impl.DefaultDatabaseTelemetryFactory;
import io.koraframework.database.common.telemetry.impl.NoopDatabaseLoggerFactory;
import io.koraframework.database.common.telemetry.impl.NoopDatabaseMetricsFactory;
import io.koraframework.database.cassandra.mapper.result.CassandraRowMapper;
import io.koraframework.test.cassandra.CassandraParams;
import io.koraframework.test.cassandra.CassandraTestContainer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.TracerProvider;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@ExtendWith(CassandraTestContainer.class)
class CassandraDataSourceTest {
    private static CassandraDataSource createCassandraDataSource(CassandraParams params) {
        var profiles = new HashMap<String, CassandraConfig.Profile>();
        profiles.put(
            "profile",
            new $CassandraConfig_Profile_ConfigValueMapper.Profile_Impl(new $CassandraConfig_Profile_ProfileBasic_ConfigValueMapper.ProfileBasic_Impl(
                List.of(),
                new $CassandraConfig_Basic_BasicRequestConfig_ConfigValueMapper.BasicRequestConfig_Impl(Duration.ofSeconds(10), null, null, null, null),
                null,
                null,
                null,
                null,
                null
            ), null)
        );
        var config = new $CassandraConfig_ConfigValueMapper.CassandraConfig_Impl(
            profiles,
            new $CassandraConfig_Basic_ConfigValueMapper.Basic_Impl(
                null,
                null,
                List.of(params.host() + ":" + params.port()),
                params.dc(),
                params.keyspace(),
                null,
                null
            ),
            new $CassandraConfig_Advanced_ConfigValueMapper.Advanced_Impl(
                null, null, null, null, null, null, null, null, null,
                new $CassandraConfig_Advanced_MetricsConfig_ConfigValueMapper.MetricsConfig_Impl(
                    new $CassandraConfig_Advanced_MetricsConfig_IdGenerator_ConfigValueMapper.IdGenerator_Defaults(),
                    null, null, false
                ),
                null, null, null, null, null, null, null, null, null
            ),
            params.username() == null ? null : new $CassandraConfig_CassandraCredentials_ConfigValueMapper.CassandraCredentials_Impl(
                params.username(),
                params.password()
            ),
            new $DatabaseTelemetryConfig_ConfigValueMapper.DatabaseTelemetryConfig_Impl(
                new $DatabaseTelemetryConfig_DatabaseLoggingConfig_ConfigValueMapper.DatabaseLoggingConfig_Impl(true),
                new $DatabaseTelemetryConfig_DatabaseMetricsConfig_ConfigValueMapper.DatabaseMetricsConfig_Impl(true, true, new Duration[0], Map.of()),
                new $DatabaseTelemetryConfig_DatabaseTracingConfig_ConfigValueMapper.DatabaseTracingConfig_Impl(true, Map.of())
            )
        );
        return new CassandraDataSource(config, null, null, new DefaultDatabaseTelemetryFactory(TracerProvider.noop().get(""), new CompositeMeterRegistry(), NoopDatabaseLoggerFactory.INSTANCE, NoopDatabaseMetricsFactory.INSTANCE));
    }

    private static void withDb(CassandraParams params, Consumer<CassandraDataSource> consumer) {
        var db = createCassandraDataSource(params);
        try {
            db.init();
            consumer.accept(db);
        } finally {
            db.release();
        }
    }

    @Test
    public void testCassandraQueryBuilder() {
        var status = "active";
        String value = null;
        var query = CassandraQuery.builder()
            .sql("SELECT * FROM users WHERE id = :id")
            .bindIf(" AND status = :status", "status", status, status != null)
            .bindIf(" AND value = :value", "value", value, value != null)
            .sqlIf(" ALLOW FILTERING", true)
            .param("id", 1)
            .build();

        Assertions.assertThat(query.sourceCql())
            .isEqualTo("SELECT * FROM users WHERE id = :id AND status = :status ALLOW FILTERING");
        Assertions.assertThat(query.cql())
            .isEqualTo("SELECT * FROM users WHERE id = ? AND status = ? ALLOW FILTERING");
        Assertions.assertThat(query.parameterValues())
            .containsExactly(1, "active");
    }

    @Test
    public void testCassandraQuery(CassandraParams params) {
        params.execute("create table test_table_query(id int, value varchar, status varchar, primary key (id));\n");
        params.execute("insert into test_table_query(id, value, status) values (1,'test1','active');\n");
        params.execute("insert into test_table_query(id, value, status) values (2,'test2','archived');\n");

        record Entity(Integer id, String value) {}

        withDb(params, db -> {
            var status = "active";
            var query = CassandraQuery.builder()
                .sql("SELECT id, value FROM test_table_query WHERE id = :id")
                .bindIf(" AND status = :status", "status", status, status != null)
                .sql(" ALLOW FILTERING")
                .param("id", 1)
                .build();
            CassandraRowMapper<Entity> mapper = row -> {
                var __id = row.isNull("id") ? null : row.getInt("id");
                var __value = row.getString("value");
                return new Entity(__id, __value);
            };

            var result = db.queryList(query, mapper);

            Assertions.assertThat(result)
                .hasSize(1)
                .first()
                .isEqualTo(new Entity(1, "test1"));

            var one = db.queryOne(
                CassandraQuery.builder()
                    .sql("SELECT id, value FROM test_table_query WHERE id = :id")
                    .param("id", 1)
                    .build(),
                mapper
            );
            Assertions.assertThat(one).isEqualTo(new Entity(1, "test1"));

            var optional = db.queryOptional(
                CassandraQuery.builder()
                    .sql("SELECT id, value FROM test_table_query WHERE id = :id")
                    .param("id", 999)
                    .build(),
                mapper
            );
            Assertions.assertThat(optional).isEmpty();
        });
    }


    @Test
    public void testQuery(CassandraParams params) {
        params.execute("create table test_table(id int, value varchar, primary key (id));\n");
        params.execute("insert into test_table(id, value) values (1,'test1');\n");

        record Entity(Integer id, String value) {}
        var qctx = new QueryContext(
            "SELECT id, value FROM test_table WHERE value = :value allow filtering",
            "SELECT id, value FROM test_table WHERE value = ? allow filtering"
        );

        withDb(params, db -> {
            var result = db.query(qctx, stmt -> {
                var s = stmt.bind("test1");
                return db.currentSession().execute(s).map(row -> {
                    var __id = row.isNull("id") ? null : row.getInt("id");
                    var __value = row.getString("value");
                    return new Entity(__id, __value);
                });
            });
            Assertions.assertThat(result)
                .hasSize(1)
                .first()
                .isEqualTo(new Entity(1, "test1"));
        });
    }

    @Test
    public void testAsyncQuery(CassandraParams params) {
        params.execute("create table test_table(id int, value varchar, primary key (id));\n");
        params.execute("insert into test_table(id, value) values (1,'test1');\n");

        record Entity(Integer id, String value) {}
        var qctx = new QueryContext(
            "SELECT id, value FROM test_table WHERE value = :value allow filtering",
            "SELECT id, value FROM test_table WHERE value = ? allow filtering"
        );

        withDb(params, db -> {
            var result = db.query(qctx, stmt -> {
                var s = stmt.bind("test1");
                return db.currentSession().execute(s).map(row -> {
                    var __id = row.isNull("id") ? null : row.getInt("id");
                    var __value = row.getString("value");
                    return new Entity(__id, __value);
                });
            });

            Assertions.assertThat(result)
                .hasSize(1)
                .first()
                .isEqualTo(new Entity(1, "test1"));

        });
    }
}
