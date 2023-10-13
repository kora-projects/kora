package ru.tinkoff.kora.database.vertx;

import io.netty.channel.nio.NioEventLoopGroup;
import io.vertx.sqlclient.Tuple;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.tinkoff.kora.database.common.QueryContext;
import ru.tinkoff.kora.database.common.telemetry.DefaultDataBaseTelemetryFactory;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_LogConfig_ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_MetricsConfig_ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_TracingConfig_ConfigValueExtractor;
import ru.tinkoff.kora.test.postgres.PostgresParams;
import ru.tinkoff.kora.test.postgres.PostgresTestContainer;
import ru.tinkoff.kora.vertx.common.VertxUtil;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(PostgresTestContainer.class)
class VertxConnectionFactoryTest {
    private static NioEventLoopGroup eventLoopGroup;

    @BeforeAll
    static void beforeAll() {
        eventLoopGroup = new NioEventLoopGroup(1, VertxUtil.vertxThreadFactory());
    }

    @AfterAll
    static void afterAll() {
        eventLoopGroup.shutdownGracefully();
    }

    private static void withDb(PostgresParams params, Consumer<VertxDatabase> consumer) {
        var config = new $VertxDatabaseConfig_ConfigValueExtractor.VertxDatabaseConfig_Impl(
            params.user(),
            params.password(),
            params.host(),
            params.port(),
            params.db(),
            "test",
            Duration.ofMillis(1000),
            Duration.ofMillis(1000),
            Duration.ofMillis(1000),
            1,
            true,
            false,
            Duration.ofMillis(1000),
            new $TelemetryConfig_ConfigValueExtractor.TelemetryConfig_Impl(
                new $TelemetryConfig_LogConfig_ConfigValueExtractor.LogConfig_Impl(true),
                new $TelemetryConfig_TracingConfig_ConfigValueExtractor.TracingConfig_Impl(true),
                new $TelemetryConfig_MetricsConfig_ConfigValueExtractor.MetricsConfig_Defaults()
            )
        );
        var db = new VertxDatabase(config, eventLoopGroup, new DefaultDataBaseTelemetryFactory(null, null, null));
        try {
            db.init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            consumer.accept(db);
        } finally {
            db.release();
        }
    }


    @Test
    void testQuery(PostgresParams params) {
        params.execute("""
            CREATE TABLE test_table(id BIGSERIAL, value VARCHAR);
            INSERT INTO test_table(value) VALUES ('test1');
            INSERT INTO test_table(value) VALUES ('test2');
            """
        );

        var id = "SELECT * FROM test_table WHERE value = :value";
        var sql = "SELECT * FROM test_table WHERE value = $1";
        record Entity(long id, String value) {}
        withDb(params, db -> {
            var future = db.withConnection(connection -> VertxRepositoryHelper.completionStage(db, new QueryContext(id, sql), Tuple.of("test1"), rows -> {
                assertThat(rows.size() == 1);
                var row = rows.iterator().next();
                return new Entity(row.getLong(0), row.getString(1));
            }));
            Assertions.assertThat(future)
                .succeedsWithin(Duration.ofMinutes(1))
                .isEqualTo(new Entity(1, "test1"));
        });
    }

    @Test
    void testTransaction(PostgresParams params) {
        params.execute("CREATE TABLE test_table(id BIGSERIAL, value VARCHAR);");
        var id = "INSERT INTO test_table(value) VALUES ('test1');";
        var sql = "INSERT INTO test_table(value) VALUES ('test1');";

        withDb(params, vertxDatabase -> {
            var f1 = vertxDatabase.inTx(connection -> VertxRepositoryHelper.completionStage(vertxDatabase, new QueryContext(id, sql), Tuple.tuple(), rs -> "")
                .thenApply(v -> {
                    throw new RuntimeException("test");
                }));
            Assertions.assertThat(f1).failsWithin(Duration.ofMinutes(1));

            PostgresParams.ResultSetMapper<List<String>, RuntimeException> extractor = rs -> {
                var result = new ArrayList<String>();
                try {
                    while (rs.next()) {
                        result.add(rs.getString(1));
                    }
                } catch (SQLException sqlException) {
                    throw new RuntimeException(sqlException);
                }
                return result;
            };

            var values = params.query("SELECT value FROM test_table", extractor);
            assertThat(values).hasSize(0);


            var f2 = vertxDatabase.inTx(connection -> VertxRepositoryHelper.completionStage(vertxDatabase, new QueryContext(id, sql), Tuple.tuple(), rs -> ""));
            Assertions.assertThat(f2).succeedsWithin(Duration.ofMinutes(1));

            values = params.query("SELECT value FROM test_table", extractor);
            assertThat(values).hasSize(1);
        });
    }
}
