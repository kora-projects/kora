package io.koraframework.database.cassandra;

import com.datastax.oss.driver.api.core.metrics.DefaultNodeMetric;
import com.datastax.oss.driver.api.core.metrics.DefaultSessionMetric;
import io.koraframework.database.common.QueryContext;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_ConfigValueMapper;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseLoggingConfig_ConfigValueMapper;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseMetricsConfig_ConfigValueMapper;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseTracingConfig_ConfigValueMapper;
import io.koraframework.database.common.telemetry.impl.DefaultDatabaseTelemetryFactory;
import io.koraframework.database.common.telemetry.impl.NoopDatabaseLoggerFactory;
import io.koraframework.database.common.telemetry.impl.NoopDatabaseMetricsFactory;
import io.koraframework.test.cassandra.CassandraParams;
import io.koraframework.test.cassandra.CassandraTestContainer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.trace.TracerProvider;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@ExtendWith(CassandraTestContainer.class)
class CassandraSessionTest {
    @Test
    public void testQuery(CassandraParams params) {
        params.execute("create table test_table(id int, value varchar, primary key (id));\n");
        params.execute("insert into test_table(id, value) values (1,'test1');\n");

        record Entity(Integer id, String value) {}
        var qctx = new QueryContext(
            "SELECT id, value FROM test_table WHERE value = :value allow filtering",
            "SELECT id, value FROM test_table WHERE value = ? allow filtering"
        );

        CassandraTestUtils.withDb(params, db -> {
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

        CassandraTestUtils.withDb(params, db -> {
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
    public void releaseRemovesDriverMeters(CassandraParams params) {
        var registry = new SimpleMeterRegistry();
        var contactPoint = params.host() + ":" + params.port();
        for (int i = 0; i < 3; i++) {
            var session = sessionWithDriverMetrics(params, contactPoint, registry);
            session.init();
            session.currentSession().execute("SELECT release_version FROM system.local");
            Assertions.assertThat(registry.getMeters()).isNotEmpty();
            session.release();
            Assertions.assertThat(registry.getMeters()).isEmpty();
        }
    }

    @Test
    public void failedInitRemovesDriverMeters(CassandraParams params) {
        var registry = new SimpleMeterRegistry();
        var session = sessionWithDriverMetrics(params, "127.0.0.1:1", registry);
        Assertions.assertThatThrownBy(session::init).isInstanceOf(IllegalStateException.class);
        session.release();
        Assertions.assertThat(registry.getMeters()).isEmpty();
    }

    private static CassandraSession sessionWithDriverMetrics(CassandraParams params, String contactPoint, MeterRegistry registry) {
        var histogram = new $CassandraConfig_Advanced_MetricsConfig_Config_ConfigValueMapper.Config_Impl(
            Duration.ofMillis(1), Duration.ofSeconds(90), 3, null, new Duration[0]);
        var nodeMetrics = new $CassandraConfig_Advanced_MetricsConfig_NodeConfig_ConfigValueMapper.NodeConfig_Impl(
            List.of(DefaultNodeMetric.CQL_MESSAGES.getPath()), histogram);
        var sessionMetrics = new $CassandraConfig_Advanced_MetricsConfig_SessionConfig_ConfigValueMapper.SessionConfig_Impl(
            List.of(DefaultSessionMetric.CQL_REQUESTS.getPath()), histogram, histogram);
        var config = new $CassandraConfig_ConfigValueMapper.CassandraConfig_Impl(
            Map.of(),
            new $CassandraConfig_Basic_ConfigValueMapper.Basic_Impl(null, null, List.of(contactPoint), params.dc(), params.keyspace(), null, null),
            new $CassandraConfig_Advanced_ConfigValueMapper.Advanced_Impl(
                null, null, null, null, null, null, null, null, null,
                new $CassandraConfig_Advanced_MetricsConfig_ConfigValueMapper.MetricsConfig_Impl(
                    new $CassandraConfig_Advanced_MetricsConfig_IdGenerator_ConfigValueMapper.IdGenerator_Defaults(), nodeMetrics, sessionMetrics, false),
                null, null, null, null, null, null, null, null, null),
            params.username() == null ? null : new $CassandraConfig_CassandraCredentials_ConfigValueMapper.CassandraCredentials_Impl(params.username(), params.password()),
            new $DatabaseTelemetryConfig_ConfigValueMapper.DatabaseTelemetryConfig_Impl(
                new $DatabaseTelemetryConfig_DatabaseLoggingConfig_ConfigValueMapper.DatabaseLoggingConfig_Impl(true),
                new $DatabaseTelemetryConfig_DatabaseMetricsConfig_ConfigValueMapper.DatabaseMetricsConfig_Impl(true, true, new Duration[0], Map.of()),
                new $DatabaseTelemetryConfig_DatabaseTracingConfig_ConfigValueMapper.DatabaseTracingConfig_Impl(true, Map.of())));
        var telemetryFactory = new DefaultDatabaseTelemetryFactory(TracerProvider.noop().get(""), registry, NoopDatabaseLoggerFactory.INSTANCE, NoopDatabaseMetricsFactory.INSTANCE);
        return new CassandraSession(config, telemetryFactory, null, null);
    }
}
