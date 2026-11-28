package org.flywaydb.core.internal.jdbc;

import org.flywaydb.core.api.*;
import org.flywaydb.core.api.configuration.*;
import org.flywaydb.core.api.logging.*;
import org.flywaydb.core.internal.database.*;
import org.flywaydb.core.internal.exception.*;
import org.flywaydb.core.internal.util.*;

import javax.sql.*;
import java.io.*;
import java.sql.*;

public class JdbcConnectionFactory implements Closeable {
    private static final Log LOG = LogFactory.getLog(JdbcConnectionFactory.class);
    private final DataSource dataSource;
    private final int connectRetries;
    private final int connectRetriesInterval;
    private final Configuration configuration;
    private final DatabaseType databaseType;
    private final String jdbcUrl;
    private final String driverInfo;
    private final String productName;
    private final StatementInterceptor statementInterceptor;
    private Connection firstConnection;
    private ConnectionInitializer connectionInitializer;
    private final boolean supportsBatch;

    public JdbcConnectionFactory(DataSource dataSource, Configuration configuration, StatementInterceptor statementInterceptor) {
        this.dataSource = dataSource;
        this.connectRetries = 0;
        this.connectRetriesInterval = 120;
        this.configuration = configuration;
        this.statementInterceptor = statementInterceptor;

        this.firstConnection = JdbcUtils.openConnection(dataSource, this.connectRetries, this.connectRetriesInterval);
        this.databaseType = new org.flywaydb.database.postgresql.PostgreSQLDatabaseType();

        var databaseMetaData = JdbcUtils.getDatabaseMetaData(this.firstConnection);
        this.jdbcUrl = getJdbcUrl(databaseMetaData);
        this.driverInfo = getDriverInfo(databaseMetaData);
        this.productName = JdbcUtils.getDatabaseProductName(databaseMetaData);
        this.supportsBatch = this.determineBatchSupport(databaseMetaData);
        this.firstConnection = this.databaseType.alterConnectionAsNeeded(this.firstConnection, configuration);
    }

    public void setConnectionInitializer(ConnectionInitializer connectionInitializer) {
        this.connectionInitializer = connectionInitializer;
    }

    private boolean determineBatchSupport(DatabaseMetaData databaseMetaData) {
        try {
            return databaseMetaData.supportsBatchUpdates();
        } catch (SQLException e) {
            LOG.debug("Unable to check whether batch updates are supported:\n" + ExceptionUtils.toMessage(e));
            return false;
        }
    }

    public Connection openConnection() throws FlywayException {
        Connection connection = this.firstConnection == null ? JdbcUtils.openConnection(this.dataSource, this.connectRetries, this.connectRetriesInterval) : this.firstConnection;
        this.firstConnection = null;
        if (this.connectionInitializer != null) {
            this.connectionInitializer.initialize(this, connection);
        }

        if (this.statementInterceptor != null) {
            if (this.databaseType.supportsReadOnlyTransactions()) {
                try {
                    connection.setReadOnly(true);
                } catch (SQLException e) {
                    throw new FlywaySqlException("Unable to switch connection to read-only", e);
                }
            }

            return this.statementInterceptor.createConnectionProxy(connection);
        } else {
            connection = this.databaseType.alterConnectionAsNeeded(connection, this.configuration);
            return connection;
        }
    }

    public void close() {
        if (this.firstConnection != null) {
            try {
                this.firstConnection.close();
            } catch (Exception e) {
                LOG.error("Error while closing connection: " + e.getMessage(), e);
            }

            this.firstConnection = null;
        }
    }

    private static String getJdbcUrl(DatabaseMetaData databaseMetaData) {
        String url;
        try {
            url = databaseMetaData.getURL();
        } catch (SQLException e) {
            throw new FlywaySqlException("Unable to retrieve the JDBC connection URL!", e);
        }

        return url == null ? "" : url;
    }

    private static String getDriverInfo(DatabaseMetaData databaseMetaData) {
        try {
            return databaseMetaData.getDriverName() + " " + databaseMetaData.getDriverVersion();
        } catch (SQLException e) {
            throw new FlywaySqlException("Unable to read database driver info: " + e.getMessage(), e);
        }
    }

    public Configuration getConfiguration() {
        return this.configuration;
    }

    public DatabaseType getDatabaseType() {
        return this.databaseType;
    }

    public String getJdbcUrl() {
        return this.jdbcUrl;
    }

    public String getDriverInfo() {
        return this.driverInfo;
    }

    public String getProductName() {
        return this.productName;
    }

    public boolean isSupportsBatch() {
        return this.supportsBatch;
    }

    public interface ConnectionInitializer {
        void initialize(JdbcConnectionFactory var1, Connection var2);
    }
}
