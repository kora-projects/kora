package io.koraframework.database.cassandra;

import com.datastax.oss.driver.api.core.metrics.DefaultNodeMetric;
import com.datastax.oss.driver.api.core.metrics.DefaultSessionMetric;
import io.koraframework.database.common.QueryContext;
import io.koraframework.test.cassandra.CassandraParams;
import io.koraframework.test.cassandra.CassandraTestContainer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

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
    public void testDriverMetricsWithoutSignificantDigitsUseDriverDefault(CassandraParams params) {
        var histogram = new $CassandraConfig_Advanced_MetricsConfig_Config_ConfigValueMapper.Config_Defaults();
        var nodeMetrics = new $CassandraConfig_Advanced_MetricsConfig_NodeConfig_ConfigValueMapper.NodeConfig_Impl(
            List.of(DefaultNodeMetric.CQL_MESSAGES.getPath()), histogram
        );
        var sessionMetrics = new $CassandraConfig_Advanced_MetricsConfig_SessionConfig_ConfigValueMapper.SessionConfig_Impl(
            List.of(DefaultSessionMetric.CQL_REQUESTS.getPath(), DefaultSessionMetric.THROTTLING_DELAY.getPath()), histogram, histogram
        );
        var db = CassandraTestUtils.createCassandraDataSource(params, nodeMetrics, sessionMetrics);

        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(60), () -> CassandraTestUtils.withDb(db, session -> {
            var row = session.currentSession().execute("SELECT release_version FROM system.local").one();
            Assertions.assertThat(row).isNotNull();
        }));
    }

    @Test
    public void testTimestampColumnMapsToLocalDateTimeAndZonedDateTime(CassandraParams params) {
        params.execute("create table test_timestamp(id int, ldt timestamp, zdt timestamp, primary key (id));\n");
        var ldt = LocalDateTime.of(2024, 1, 2, 3, 4, 5);
        var zdt = ZonedDateTime.of(2024, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC);
        var mappers = new CassandraMapperModule() {};

        CassandraTestUtils.withDb(params, db -> {
            var insert = new QueryContext(
                "INSERT INTO test_timestamp(id, ldt, zdt) VALUES (:id, :ldt, :zdt)",
                "INSERT INTO test_timestamp(id, ldt, zdt) VALUES (?, ?, ?)"
            );
            db.query(insert, stmt -> {
                var s = stmt.boundStatementBuilder().setInt(0, 1);
                mappers.localDateTimeCassandraParameterColumnMapper().apply(s, 1, ldt);
                mappers.zonedDateTimeCassandraParameterColumnMapper().apply(s, 2, zdt);
                return db.currentSession().execute(s.build());
            });

            var select = new QueryContext(
                "SELECT ldt, zdt FROM test_timestamp WHERE id = :id",
                "SELECT ldt, zdt FROM test_timestamp WHERE id = ?"
            );
            var row = db.query(select, stmt -> db.currentSession().execute(stmt.bind(1)).one());
            Assertions.assertThat(mappers.localDateTimeCassandraRowColumnMapper().apply(row, 0)).isEqualTo(ldt);
            Assertions.assertThat(mappers.zonedDateTimeCassandraRowColumnMapper().apply(row, 1).toInstant()).isEqualTo(zdt.toInstant());
        });
    }
}
