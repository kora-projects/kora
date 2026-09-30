package io.koraframework.scheduling.db;

import com.github.kagkarlsson.scheduler.SchedulerClient;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import io.koraframework.scheduling.db.scheduler.DbSchedulerConfig;
import io.koraframework.scheduling.db.scheduler.util.DbSchedulerInitializerUtils;
import io.koraframework.test.postgres.PostgresParams;
import io.koraframework.test.postgres.PostgresTestContainer;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(PostgresTestContainer.class)
class DbSchedulerSchemaTest {

    private static final DbSchedulerConfig DEFAULT_CONFIG = () -> new DbSchedulerConfig.PollingConfig() {};
    private static final String CHANGELOG = "db/kora/scheduling-db-scheduler/liquibase/changelog.yaml";

    @Test
    void bundledSchemaResourcesCreateDefaultTableWithPrefixedObjects() throws IOException {
        var paths = Pattern.compile("path: (\\S+)").matcher(resource(CHANGELOG)).results().map(r -> r.group(1)).toList();

        assertThat(paths)
            .map(path -> path.substring(path.lastIndexOf('/') + 1))
            .containsExactlyInAnyOrder("postgresql.sql", "mysql.sql", "mariadb.sql", "mssql.sql", "oracle.sql", "hsql.sql");
        for (var path : paths) {
            var schema = resource(path);
            assertThat(schema)
                .as(path)
                .containsPattern("(?i)create table " + DEFAULT_CONFIG.tableName() + "\\s")
                .contains("constraint " + DEFAULT_CONFIG.tableName() + "_pk primary key");
            assertThat(Pattern.compile("task_(?:name|instance)\\s+varchar\\((\\d+)\\)").matcher(schema).results().map(r -> Integer.parseInt(r.group(1))).toList())
                .as(path)
                .allMatch(length -> length >= 350, "task name and instance fit 350 characters");
            assertThat(objectNames(schema))
                .as(path)
                .allMatch(name -> name.startsWith(DEFAULT_CONFIG.tableName() + "_"))
                .allMatch(name -> name.length() <= 63, "fits PostgreSQL identifier length");
        }
    }

    @Test
    void bundledSchemaIsNotDiscoveredAsFlywayMigrations(PostgresParams params) {
        var flyway = Flyway.configure()
            .dataSource(dataSource(params))
            .locations("classpath:db")
            .load();

        assertThat(flyway.info().all()).isEmpty();
    }

    @Test
    void defaultTableIsCreatedByLiquibaseChangelogOnce(PostgresParams params) throws Exception {
        var dataSource = dataSource(params);

        liquibaseUpdate(dataSource);
        liquibaseUpdate(dataSource);

        assertSchedulerCanUseTable(dataSource, DEFAULT_CONFIG.tableName());
        assertThat(changeSetExecutions(dataSource)).containsExactly("kora-scheduling-db-scheduler-jobs-create-table-postgresql:EXECUTED");
    }

    @Test
    void liquibaseChangelogMarksRanWhenTableExists(PostgresParams params) throws Exception {
        var dataSource = dataSource(params);
        DbSchedulerInitializerUtils.initializeTable(dataSource, DEFAULT_CONFIG.tableName());

        liquibaseUpdate(dataSource);

        assertSchedulerCanUseTable(dataSource, DEFAULT_CONFIG.tableName());
        assertThat(changeSetExecutions(dataSource)).containsExactly("kora-scheduling-db-scheduler-jobs-create-table-postgresql:MARK_RAN");
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

    private static List<String> objectNames(String schema) {
        return Pattern.compile("(?i)(?:constraint|index)\\s+(\\w+)").matcher(schema).results().map(r -> r.group(1)).toList();
    }

    private static void liquibaseUpdate(DataSource dataSource) throws Exception {
        try (var connection = dataSource.getConnection()) {
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database)) {
                liquibase.update();
            }
        }
    }

    private static List<String> changeSetExecutions(DataSource dataSource) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var rs = statement.executeQuery("select id, exectype from databasechangelog order by orderexecuted")) {
            var result = new ArrayList<String>();
            while (rs.next()) {
                result.add(rs.getString(1) + ":" + rs.getString(2));
            }
            return result;
        }
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
