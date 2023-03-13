package ru.tinkoff.kora.database.vertx;

import io.netty.channel.nio.NioEventLoopGroup;
import io.vertx.sqlclient.Tuple;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.tinkoff.kora.database.common.QueryContext;
import ru.tinkoff.kora.database.common.telemetry.DefaultDataBaseTelemetryFactory;
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
        var config = new VertxDatabaseConfig(
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
            true
        );
        var db = new VertxDatabase(config, eventLoopGroup, new DefaultDataBaseTelemetryFactory(null, null, null));
        db.init().block();
        try {
            consumer.accept(db);
        } finally {
            db.release().block();
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
            db.withConnection(connection -> VertxRepositoryHelper.mono(db, new QueryContext(id, sql), Tuple.of("test1"), rows -> {
                    assertThat(rows.size() == 1);
                    var row = rows.iterator().next();
                    return new Entity(row.getLong(0), row.getString(1));
                }))
                .as(StepVerifier::create)
                .expectNext(new Entity(1, "test1"))
                .verifyComplete();
        });
    }

    @Test
    void testTransaction(PostgresParams params) {
        params.execute("CREATE TABLE test_table(id BIGSERIAL, value VARCHAR);");
        var id = "INSERT INTO test_table(value) VALUES ('test1');";
        var sql = "INSERT INTO test_table(value) VALUES ('test1');";

        withDb(params, vertxDatabase -> {
            vertxDatabase.inTx(connection -> VertxRepositoryHelper.mono(vertxDatabase, new QueryContext(id, sql), Tuple.tuple(), rs -> "")
                    .then(Mono.error(new RuntimeException("test"))))
                .as(StepVerifier::create)
                .verifyError();

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


            vertxDatabase.inTx(connection -> VertxRepositoryHelper.mono(vertxDatabase, new QueryContext(id, sql), Tuple.tuple(), rs -> ""))
                .as(StepVerifier::create)
                .verifyComplete();

            values = params.query("SELECT value FROM test_table", extractor);
            assertThat(values).hasSize(1);
        });
    }
}
