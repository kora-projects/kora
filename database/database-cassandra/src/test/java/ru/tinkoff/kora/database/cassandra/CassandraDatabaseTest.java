package ru.tinkoff.kora.database.cassandra;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.tinkoff.kora.database.common.QueryContext;
import ru.tinkoff.kora.database.common.telemetry.DefaultDataBaseTelemetryFactory;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_LogConfig_ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_MetricsConfig_ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_TracingConfig_ConfigValueExtractor;
import ru.tinkoff.kora.test.cassandra.CassandraParams;
import ru.tinkoff.kora.test.cassandra.CassandraTestContainer;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

@ExtendWith(CassandraTestContainer.class)
class CassandraDatabaseTest {
    private static CassandraDatabase createCassandraDatabase(CassandraParams params) {
        var profiles = new HashMap<String, CassandraConfig.Profile>();
        profiles.put(
            "profile",
            new $CassandraConfig_Profile_ConfigValueExtractor.Profile_Impl(new $CassandraConfig_Profile_ProfileBasic_ConfigValueExtractor.ProfileBasic_Impl(
                List.of(),
                new $CassandraConfig_Basic_BasicRequestConfig_ConfigValueExtractor.BasicRequestConfig_Impl(Duration.ofSeconds(10), null, null, null, null),
                null,
                null,
                null,
                null,
                null
            ), null)
        );
        var config = new $CassandraConfig_ConfigValueExtractor.CassandraConfig_Impl(
            profiles,
            new $CassandraConfig_Basic_ConfigValueExtractor.Basic_Impl(
                null,
                null,
                List.of(params.host() + ":" + params.port()),
                params.dc(),
                params.keyspace(),
                null,
                null
            ),
            new $CassandraConfig_Advanced_ConfigValueExtractor.Advanced_Impl(
                null, null, null, null, null, null, null, null, null,
                new $CassandraConfig_Advanced_MetricsConfig_ConfigValueExtractor.MetricsConfig_Impl(
                    new $CassandraConfig_Advanced_MetricsConfig_IdGenerator_ConfigValueExtractor.IdGenerator_Defaults(),
                    null, null, false
                ),
                null, null, null, null, null, null, null, null, null
            ),
            params.username() == null ? null : new $CassandraConfig_CassandraCredentials_ConfigValueExtractor.CassandraCredentials_Impl(
                params.username(),
                params.password()
            ),
            new $TelemetryConfig_ConfigValueExtractor.TelemetryConfig_Impl(
                new $TelemetryConfig_LogConfig_ConfigValueExtractor.LogConfig_Impl(true),
                new $TelemetryConfig_TracingConfig_ConfigValueExtractor.TracingConfig_Impl(true),
                new $TelemetryConfig_MetricsConfig_ConfigValueExtractor.MetricsConfig_Impl(null, null)
            )
        );
        return new CassandraDatabase(config, (b, _) -> b, new DefaultDataBaseTelemetryFactory(null, null, null));
    }

    private static void withDb(CassandraParams params, Consumer<CassandraDatabase> consumer) {
        var db = createCassandraDatabase(params);
        try {
            db.init();
            consumer.accept(db);
        } finally {
            db.release();
        }
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
