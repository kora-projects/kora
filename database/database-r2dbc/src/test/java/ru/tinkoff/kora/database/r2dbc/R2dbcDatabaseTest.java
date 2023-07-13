package ru.tinkoff.kora.database.r2dbc;

import io.r2dbc.spi.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.test.StepVerifier;
import ru.tinkoff.kora.database.common.QueryContext;
import ru.tinkoff.kora.database.common.telemetry.DefaultDataBaseTelemetryFactory;
import ru.tinkoff.kora.test.postgres.PostgresParams;
import ru.tinkoff.kora.test.postgres.PostgresTestContainer;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(PostgresTestContainer.class)
class R2dbcDatabaseTest {
    private static void withDb(PostgresParams params, Consumer<R2dbcDatabase> consumer) {
        var config = new $R2dbcDatabaseConfig_ConfigValueExtractor.R2dbcDatabaseConfig_Impl(
            "r2dbc:postgres://%s:%d/%s".formatted(params.host(), params.port(), params.db()),
            params.user(),
            params.password(),
            "test",
            Duration.ofMillis(1000L),
            Duration.ofMillis(1000L),
            Duration.ofMillis(1000L),
            Duration.ofMillis(10000L),
            Duration.ofMillis(10000L),
            3,
            2,
            0,
            Collections.emptyMap()
        );
        var db = new R2dbcDatabase(config, List.of(), new DefaultDataBaseTelemetryFactory(null, null, null));
        db.init();
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

        withDb(params, db -> db.query(
                new QueryContext(id, sql),
                st -> st.bind("$1", "test1"),
                rsf -> rsf.flatMap(rs -> rs.map((row, meta) -> new Entity(row.get(0, Long.class), row.get(1, String.class))))
                    .collectList())
            .flatMapIterable(Function.identity())
            .as(StepVerifier::create)
            .expectNext(new Entity(1, "test1"))
            .verifyComplete());
    }

    @Test
    void testTransaction(PostgresParams params) {
        params.execute("CREATE TABLE test_table(id BIGSERIAL, value VARCHAR);");
        var id = "INSERT INTO test_table(value) VALUES ('test1');";
        var sql = "INSERT INTO test_table(value) VALUES ('test1');";

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

        withDb(params, db -> {
            var values = params.query("SELECT value FROM test_table", extractor);
            assertThat(values).hasSize(0);

            var updated = db.query(new QueryContext(id, sql), st -> {}, rs -> rs.flatMap(Result::getRowsUpdated).reduce(0L, Long::sum))
                .block();
            assertThat(updated).isEqualTo(1);

            values = params.query("SELECT value FROM test_table", extractor);
            assertThat(values).hasSize(1);
        });

    }
}
