package io.koraframework.database.jdbc;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_ConfigValueMapper;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseLoggingConfig_ConfigValueMapper;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseMetricsConfig_ConfigValueMapper;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseTracingConfig_ConfigValueMapper;
import io.koraframework.database.common.telemetry.impl.DefaultDatabaseTelemetryFactory;
import io.koraframework.database.common.telemetry.impl.NoopDatabaseLoggerFactory;
import io.koraframework.database.common.telemetry.impl.NoopDatabaseMetricsFactory;
import io.koraframework.test.postgres.PostgresParams;
import io.koraframework.test.postgres.PostgresTestContainer;
import io.koraframework.micrometer.common.NoopMeterRegistry;
import io.opentelemetry.api.trace.TracerProvider;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@ExtendWith({PostgresTestContainer.class})
class JdbcDataSourceTest {
    static {
        if (LoggerFactory.getLogger("ROOT") instanceof Logger log) {
            log.setLevel(Level.INFO);
        }
        if (LoggerFactory.getLogger("io.koraframework") instanceof Logger log) {
            log.setLevel(Level.DEBUG);
        }
    }

    private static void withDb(PostgresParams params, Consumer<JdbcDataSource> consumer) throws SQLException {
        var config = new $JdbcDatabaseConfig_ConfigValueMapper.JdbcDatabaseConfig_Impl(
            params.user(),
            params.password(),
            params.jdbcUrl(),
            "testPool",
            null,
            Duration.ofMillis(1000L),
            Duration.ofMillis(1000L),
            Duration.ofMillis(1000L),
            Duration.ofMillis(1000L),
            Duration.ofMillis(1000L),
            1,
            0,
            Duration.ofMillis(1000L),
            false,
            new Properties(),
            new $DatabaseTelemetryConfig_ConfigValueMapper.DatabaseTelemetryConfig_Impl(
                new $DatabaseTelemetryConfig_DatabaseLoggingConfig_ConfigValueMapper.DatabaseLoggingConfig_Impl(true),
                new $DatabaseTelemetryConfig_DatabaseMetricsConfig_ConfigValueMapper.DatabaseMetricsConfig_Impl(true, true, new Duration[0], Map.of()),
                new $DatabaseTelemetryConfig_DatabaseTracingConfig_ConfigValueMapper.DatabaseTracingConfig_Impl(true, Map.of())
            )
        );
        var db = new JdbcDataSource(config, new DefaultDatabaseTelemetryFactory(TracerProvider.noop().get(""), NoopMeterRegistry.INSTANCE, NoopDatabaseLoggerFactory.INSTANCE, NoopDatabaseMetricsFactory.INSTANCE), null);
        db.init();
        try {
            consumer.accept(db);
        } finally {
            db.release();
        }
    }

    @Test
    void testQuery(PostgresParams params) throws SQLException {
        var tableName = PostgresTestContainer.randomName("test_table");
        params.execute("""
            CREATE TABLE %s(id BIGSERIAL, value VARCHAR);
            INSERT INTO %s(value) VALUES ('test1');
            INSERT INTO %s(value) VALUES ('test2');
            """.formatted(tableName, tableName, tableName));

        var id = "SELECT * FROM %s WHERE value = :value".formatted(tableName);
        var sql = "SELECT * FROM %s WHERE value = ?".formatted(tableName);
        record Entity(long id, String value) {}


        withDb(params, db -> {
            var result = db.withConnection(() -> {
                var r = new ArrayList<Entity>();
                try (var stmt = db.connectionCurrent().prepareStatement(sql);) {
                    stmt.setString(1, "test1");
                    var rs = stmt.executeQuery();
                    while (rs.next()) {
                        r.add(new Entity(rs.getInt(1), rs.getString(2)));
                    }
                }
                return r;
            });
            Assertions.assertThat(result).containsExactly(new Entity(1, "test1"));
        });
    }

    @Test
    void testTransaction(PostgresParams params) throws SQLException {
        var tableName = "test_table_" + PostgresTestContainer.randomName("test_table");
        params.execute("CREATE TABLE %s(id BIGSERIAL, value VARCHAR);".formatted(tableName));
        var id = "INSERT INTO %s(value) VALUES ('test1');".formatted(tableName);
        var sql = "INSERT INTO %s(value) VALUES ('test1');".formatted(tableName);
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
            var latch = new CountDownLatch(1);
            Assertions.assertThatThrownBy(() -> db.inTx((JdbcExecutor.SqlRunnable) () -> {
                db.currentContext().afterRollback((conn, e) -> latch.countDown());
                try (var stmt = db.connectionCurrent().prepareStatement(sql)) {
                    stmt.execute();
                }
                throw new RuntimeException();
            }));

            Assertions.assertThat(latch.getCount()).isEqualTo(0);

            var values = params.query("SELECT value FROM %s".formatted(tableName), extractor);
            Assertions.assertThat(values).hasSize(0);

            var latch1 = new CountDownLatch(1);
            db.inTx(() -> {
                db.currentContext().afterCommit((conn) -> latch1.countDown());
                try (var stmt = db.connectionCurrent().prepareStatement(sql)) {
                    stmt.execute();
                }
            });

            Assertions.assertThat(latch1.getCount()).isEqualTo(0);

            values = params.query("SELECT value FROM %s".formatted(tableName), extractor);
            Assertions.assertThat(values).hasSize(1);
        });
    }

    @Test
    void testPostTransactionActionsDoNotLeakIntoNextTransaction(PostgresParams params) throws SQLException {
        withDb(params, db -> {
            var calls = new ArrayList<String>();
            db.withConnection(() -> {
                db.inTx(() -> {
                    db.currentContext().afterRollback((conn, e) -> calls.add("rollback action of committed tx"));
                });
                Assertions.assertThatThrownBy(() -> db.inTx((JdbcExecutor.SqlRunnable) () -> {
                    db.currentContext().afterCommit(conn -> calls.add("commit action of rolled back tx"));
                    throw new IllegalStateException();
                }));
                db.inTx(() -> {});
            });

            Assertions.assertThat(calls).isEmpty();
        });
    }

    @Test
    void testPostCommitActionRunsAnotherTransaction(PostgresParams params) throws SQLException {
        withDb(params, db -> {
            var calls = new ArrayList<String>();
            db.inTx(() -> {
                db.currentContext().afterCommit(conn -> {
                    calls.add("first");
                    db.inTx(() -> {
                        db.currentContext().afterCommit(c -> calls.add("second"));
                    });
                });
            });

            Assertions.assertThat(calls).containsExactly("first", "second");
        });
    }

    @Test
    void testPostTransactionActionsAllRunWhenOneFails(PostgresParams params) throws SQLException {
        withDb(params, db -> {
            var calls = new ArrayList<String>();
            Assertions.assertThatThrownBy(() -> db.inTx(() -> {
                db.currentContext().afterCommit(conn -> {
                    throw new IllegalStateException("commit action");
                });
                db.currentContext().afterCommit(conn -> calls.add("commit"));
            })).hasMessage("commit action");

            var failure = new IllegalStateException("tx");
            Assertions.assertThatThrownBy(() -> db.inTx((JdbcExecutor.SqlRunnable) () -> {
                db.currentContext().afterRollback((conn, e) -> {
                    throw new IllegalStateException("rollback action");
                });
                db.currentContext().afterRollback((conn, e) -> calls.add("rollback"));
                throw failure;
            })).isSameAs(failure).hasSuppressedException(new IllegalStateException("rollback action"));

            Assertions.assertThat(calls).containsExactly("commit", "rollback");
        });
    }

    @Test
    void testPostCommitActionsAllRunWhenOneThrowsError(PostgresParams params) throws SQLException {
        withDb(params, db -> {
            var calls = new ArrayList<String>();
            var error = new AssertionError("commit action");
            Assertions.assertThatThrownBy(() -> db.inTx(() -> {
                db.currentContext().afterCommit(conn -> {
                    throw error;
                });
                db.currentContext().afterCommit(conn -> calls.add("commit"));
            })).isSameAs(error);

            Assertions.assertThat(calls).containsExactly("commit");
        });
    }

    @Test
    void testPostRollbackActionsAllRunWhenOneThrowsError(PostgresParams params) throws SQLException {
        withDb(params, db -> {
            var calls = new ArrayList<String>();
            var failure = new IllegalStateException("tx");
            var error = new AssertionError("rollback action");
            Assertions.assertThatThrownBy(() -> db.inTx((JdbcExecutor.SqlRunnable) () -> {
                db.currentContext().afterRollback((conn, e) -> {
                    throw error;
                });
                db.currentContext().afterRollback((conn, e) -> calls.add("rollback"));
                throw failure;
            })).isSameAs(failure);

            Assertions.assertThat(failure.getSuppressed()).containsExactly(error);
            Assertions.assertThat(calls).containsExactly("rollback");
        });
    }

    @Test
    void testPostRollbackActionsRunWhenConnectionIsTerminated(PostgresParams params) throws SQLException {
        withDb(params, db -> {
            var calls = new ArrayList<Throwable>();
            var failure = new IllegalStateException("tx");
            Assertions.assertThatThrownBy(() -> db.inTx((JdbcExecutor.SqlRunnable) () -> {
                db.currentContext().afterRollback((conn, e) -> calls.add(e));
                long pid;
                try (var stmt = db.currentConnection().prepareStatement("SELECT pg_backend_pid()"); var rs = stmt.executeQuery()) {
                    rs.next();
                    pid = rs.getLong(1);
                }
                params.execute("SELECT pg_terminate_backend(" + pid + ", 5000)");
                throw failure;
            })).isSameAs(failure);

            Assertions.assertThat(failure.getSuppressed()).isNotEmpty();
            Assertions.assertThat(calls).containsExactly(failure);
        });
    }

    @Test
    void testPostRollbackActionsRunWhenTransactionThreadIsInterrupted(PostgresParams params) throws SQLException {
        withDb(params, db -> {
            var calls = new ArrayList<String>();
            var started = new CountDownLatch(1);
            var thrown = new AtomicReference<Throwable>();
            var thread = Thread.ofVirtual().start(() -> {
                try {
                    db.inTx(ctx -> {
                        ctx.afterRollback((conn, e) -> calls.add("rollback"));
                        started.countDown();
                        try (var stmt = ctx.connection().prepareStatement("SELECT pg_sleep(5)")) {
                            stmt.execute();
                        }
                    });
                } catch (Throwable e) {
                    thrown.set(e);
                }
            });
            try {
                started.await();
                Thread.sleep(300);
                thread.interrupt();
                thread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            Assertions.assertThat(thrown.get()).isNotNull();
            Assertions.assertThat(calls).containsExactly("rollback");
        });
    }

    @Test
    void testTransactionIsolationLevel(PostgresParams params) throws SQLException {
        withDb(params, db -> {
            var previousIsolationLevel = db.withConnection(Connection::getTransactionIsolation);

            db.inTx(JdbcExecutor.TxIsolation.SERIALIZABLE, context -> {
                Assertions.assertThat(context.connection().getTransactionIsolation())
                    .isEqualTo(Connection.TRANSACTION_SERIALIZABLE);
            });

            var currentIsolationLevel = db.withConnection(Connection::getTransactionIsolation);
            Assertions.assertThat(currentIsolationLevel).isEqualTo(previousIsolationLevel);
        });
    }
}
