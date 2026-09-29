package io.koraframework.scheduling.db;

import com.github.kagkarlsson.scheduler.SchedulerClient;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import io.koraframework.scheduling.db.scheduler.DbSchedulerConfig;
import io.koraframework.scheduling.db.scheduler.util.DbSchedulerInitializerUtils;
import io.koraframework.test.postgres.PostgresParams;
import io.koraframework.test.postgres.PostgresTestContainer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(PostgresTestContainer.class)
class DbSchedulerSchemaTest {

    private static final DbSchedulerConfig DEFAULT_CONFIG = () -> new DbSchedulerConfig.PollingConfig() {};

    @Test
    void bundledSchemaResourcesCreateDefaultTable() throws IOException {
        var changelog = resource("db/scheduling-db/liquibase/changelog.yaml");
        var paths = Pattern.compile("path: (\\S+)").matcher(changelog).results().map(r -> r.group(1)).toList();

        assertThat(paths)
            .map(path -> path.split("/")[3])
            .containsExactlyInAnyOrder("postgresql", "mysql", "mariadb", "mssql", "oracle", "hsql");
        for (var path : paths) {
            assertThat(resource(path))
                .as(path)
                .containsPattern("(?i)create table " + DEFAULT_CONFIG.tableName() + "\\s");
        }
    }

    @Test
    void defaultTableIsCreatedByBundledFlywayMigration(PostgresParams params) {
        var dataSource = dataSource(params);
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/scheduling-db/flyway/postgresql")
            .load()
            .migrate();

        assertSchedulerCanUseTable(dataSource, DEFAULT_CONFIG.tableName());
    }

    @Test
    void initializesTablesWithDifferentNamesInOneSchema(PostgresParams params) throws Exception {
        var dataSource = dataSource(params);

        DbSchedulerInitializerUtils.initializeTable(dataSource, DEFAULT_CONFIG.tableName());
        DbSchedulerInitializerUtils.initializeTable(dataSource, "app_tasks");

        assertSchedulerCanUseTable(dataSource, DEFAULT_CONFIG.tableName());
        assertSchedulerCanUseTable(dataSource, "app_tasks");
    }

    @Test
    void initializesSchemaQualifiedTable(PostgresParams params) throws Exception {
        var dataSource = dataSource(params);
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("create schema app");
        }

        DbSchedulerInitializerUtils.initializeTable(dataSource, "app.jobs");

        assertSchedulerCanUseTable(dataSource, "app.jobs");
    }

    private static void assertSchedulerCanUseTable(DataSource dataSource, String tableName) {
        var task = Tasks.oneTime("schema-test").execute((instance, context) -> {});
        var client = SchedulerClient.Builder.create(dataSource, task).tableName(tableName).build();

        client.scheduleIfNotExists(task.instance("1"), Instant.now());

        assertThat(client.getScheduledExecutions()).hasSize(1);
    }

    private static DataSource dataSource(PostgresParams params) {
        var dataSource = new PGSimpleDataSource();
        dataSource.setUrl(params.jdbcUrl());
        dataSource.setUser(params.user());
        dataSource.setPassword(params.password());
        return dataSource;
    }

    private static String resource(String path) throws IOException {
        try (var is = DbSchedulerSchemaTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(is).as(path).isNotNull();
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
