package io.koraframework.scheduling.db;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class DbSchedulerTableInitializerTest {

    @Test
    void skipsInitializationWhenTableExists() throws Exception {
        var dataSource = Mockito.mock(DataSource.class);
        var connection = Mockito.mock(Connection.class);
        var statement = Mockito.mock(Statement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.createStatement()).thenReturn(statement);

        DbSchedulerTableInitializer.initialize(dataSource, "scheduled_tasks");

        Mockito.verify(statement).execute("select 1 from scheduled_tasks where 1 = 0");
        Mockito.verifyNoMoreInteractions(statement);
    }

    @Test
    void executesPostgresMigrationWithConfiguredTableName() throws Exception {
        var dataSource = Mockito.mock(DataSource.class);
        var connection = Mockito.mock(Connection.class);
        var metadata = Mockito.mock(DatabaseMetaData.class);
        var tableCheck = Mockito.mock(Statement.class);
        var createTable = Mockito.mock(Statement.class);
        var createExecutionTimeIndex = Mockito.mock(Statement.class);
        var createLastHeartbeatIndex = Mockito.mock(Statement.class);
        var createPriorityIndex = Mockito.mock(Statement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(metadata.getDriverName()).thenReturn("PostgreSQL JDBC Driver");
        when(connection.createStatement()).thenReturn(tableCheck, createTable, createExecutionTimeIndex, createLastHeartbeatIndex, createPriorityIndex);
        when(tableCheck.execute("select 1 from app_tasks where 1 = 0")).thenThrow(new SQLException("missing table"));

        DbSchedulerTableInitializer.initialize(dataSource, "app_tasks");

        var sql = ArgumentCaptor.forClass(String.class);
        Mockito.verify(createTable).execute(sql.capture());
        assertThat(sql.getValue())
            .contains("create table app_tasks")
            .doesNotContain("scheduled_tasks");
        Mockito.verify(createExecutionTimeIndex).execute("create index execution_time_idx on app_tasks (execution_time)");
        Mockito.verify(createLastHeartbeatIndex).execute("create index last_heartbeat_idx on app_tasks (last_heartbeat)");
        Mockito.verify(createPriorityIndex).execute("create index priority_execution_time_idx on app_tasks (priority desc, execution_time asc)");
    }
}
