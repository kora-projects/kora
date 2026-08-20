package io.koraframework.scheduling.db.scheduler.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

public final class DbSchedulerInitializerUtils {

    private static final Logger logger = LoggerFactory.getLogger(DbSchedulerInitializerUtils.class);
    private static final String DEFAULT_TABLE_NAME = "scheduled_tasks";
    private static final String MIGRATION_FILE = "V1__create_scheduled_tasks.sql";

    private DbSchedulerInitializerUtils() {}

    public static void initializeTable(DataSource dataSource, String tableName) throws SQLException, IOException {
        try (var connection = dataSource.getConnection()) {
            if (tableExists(connection, tableName)) {
                logger.debug("DbScheduler table '{}' already exists", tableName);
                return;
            }

            var database = database(connection);
            var sql = migration(database).replace(DEFAULT_TABLE_NAME, tableName);
            logger.info("DbScheduler initializing table '{}' for {}", tableName, database);
            execute(connection, sql);
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        var autoCommit = connection.getAutoCommit();
        try (var statement = connection.createStatement()) {
            statement.execute("select 1 from " + tableName + " where 1 = 0");
            return true;
        } catch (SQLException e) {
            if (!autoCommit) {
                connection.rollback();
            }
            return false;
        }
    }

    private static String database(Connection connection) throws SQLException {
        var productName = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        var driverName = connection.getMetaData().getDriverName().toLowerCase(Locale.ROOT);
        var name = productName + " " + driverName;
        if (name.contains("postgresql")) {
            return "postgresql";
        }
        if (name.contains("mariadb")) {
            return "mariadb";
        }
        if (name.contains("mysql")) {
            return "mysql";
        }
        if (name.contains("microsoft sql server") || name.contains("sqlserver")) {
            return "mssql";
        }
        if (name.contains("oracle")) {
            return "oracle";
        }
        if (name.contains("hsql")) {
            return "hsql";
        }
        throw new IllegalStateException("Unsupported database for DbScheduler table initialization: " + connection.getMetaData().getDatabaseProductName());
    }

    private static String migration(String database) throws IOException {
        var resource = "db/scheduling-db/flyway/" + database + "/" + MIGRATION_FILE;
        try (var is = DbSchedulerInitializerUtils.class.getClassLoader().getResourceAsStream(resource)) {
            if (is == null) {
                throw new IllegalStateException("DbScheduler table initialization migration not found: " + resource);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        for (var statementSql : sql.split(";")) {
            var statement = statementSql.strip();
            if (statement.isEmpty()) {
                continue;
            }
            try (var statementHandle = connection.createStatement()) {
                statementHandle.execute(statement);
            }
        }
    }
}
