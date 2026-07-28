package io.koraframework.database.jdbc.benchmark;

import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.SQLException;
import java.util.Locale;

public final class JdbcPoolBenchmarkRunner {

    static void main(String[] args) throws Exception {
        var postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");
        postgres.start();
        try {
            initSchema(postgres);
            var options = new OptionsBuilder()
                .include(System.getProperty("jmh.include", "JdbcPoolBenchmark"))
                .timeUnit(java.util.concurrent.TimeUnit.MICROSECONDS)
                .warmupIterations(Integer.getInteger("jmh.warmupIterations", 2))
                .measurementIterations(Integer.getInteger("jmh.iterations", 3))
                .warmupTime(TimeValue.seconds(Long.getLong("jmh.warmupSeconds", 5L)))
                .measurementTime(TimeValue.seconds(Long.getLong("jmh.measurementSeconds", 10L)))
                .forks(Integer.getInteger("jmh.forks", 1))
                .threads(Integer.getInteger("jmh.threads", 1))
                .result("build/results/jmh/jdbc-pool-benchmark.json")
                .resultFormat(ResultFormatType.JSON)
                .jvmArgsAppend(
                    "-Dbenchmark.postgres.jdbcUrl=" + postgres.getJdbcUrl(),
                    "-Dbenchmark.postgres.username=" + postgres.getUsername(),
                    "-Dbenchmark.postgres.password=" + postgres.getPassword()
                )
                .addProfiler(GCProfiler.class);

            for (var mode : System.getProperty("jmh.mode", "thrpt,sample").split(",")) {
                options.mode(parseMode(mode));
            }
            new Runner(options.build()).run();
        } finally {
            postgres.stop();
        }
    }

    private static Mode parseMode(String mode) {
        return switch (mode.trim().toLowerCase(Locale.ROOT)) {
            case "thrpt", "throughput" -> Mode.Throughput;
            case "avgt", "averagetime" -> Mode.AverageTime;
            case "sample", "sampletime" -> Mode.SampleTime;
            case "ss", "singleshottime" -> Mode.SingleShotTime;
            case "all" -> Mode.All;
            default -> throw new IllegalArgumentException("Unknown JMH mode: " + mode);
        };
    }

    private static void initSchema(PostgreSQLContainer<?> container) {
        try (var connection = container.createConnection("?");
             var statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS bench_jdbc_pool(
                    id BIGSERIAL PRIMARY KEY,
                    value VARCHAR NOT NULL
                )
                """);
            try (var rs = statement.executeQuery("SELECT count(*) FROM bench_jdbc_pool")) {
                rs.next();
                if (rs.getInt(1) > 0) {
                    return;
                }
            }
            for (int i = 0; i < 1000; i++) {
                statement.addBatch("INSERT INTO bench_jdbc_pool(value) VALUES ('value_%d')".formatted(i));
            }
            statement.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
