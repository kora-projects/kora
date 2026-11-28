package io.koraframework.database.jdbc;

import java.sql.*;
import java.util.*;

public class ConnectionContext {

    private final Connection connection;

    private List<PostCommitAction> afterCommitActions;
    private List<PostRollbackAction> afterRollbackActions;

    public ConnectionContext(Connection connection) {
        this.connection = connection;
    }

    public Connection connection() {
        return this.connection;
    }

    public ConnectionContext afterCommit(PostCommitAction action) throws SQLException {
        if (!this.isActiveTransaction()) {
            throw new IllegalStateException("Cannot add JDBC post-commit action because transaction is not active; register it inside a transactional repository/service method");
        }
        if (this.afterCommitActions == null) {
            this.afterCommitActions = new ArrayList<>(2);
        }
        this.afterCommitActions.add(action);
        return this;
    }

    Collection<PostCommitAction> takePostCommitActions() {
        var actions = this.afterCommitActions;
        if (actions == null) {
            return List.of();
        }
        this.afterCommitActions = null;
        return actions;
    }

    public ConnectionContext afterRollback(PostRollbackAction action) throws SQLException {
        if (!this.isActiveTransaction()) {
            throw new IllegalStateException("Cannot add JDBC post-rollback action because transaction is not active; register it inside a transactional repository/service method");
        }
        if (this.afterRollbackActions == null) {
            this.afterRollbackActions = new ArrayList<>(2);
        }
        this.afterRollbackActions.add(action);
        return this;
    }

    Collection<PostRollbackAction> takePostRollbackActions() {
        var actions = this.afterRollbackActions;
        if (actions == null) {
            return List.of();
        }
        this.afterRollbackActions = null;
        return actions;
    }

    @FunctionalInterface
    public interface PostCommitAction {
        void run(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface PostRollbackAction {
        void run(Connection connection, Exception e) throws SQLException;
    }

    private boolean isActiveTransaction() throws SQLException {
        return this.connection != null && !this.connection.isClosed() && !this.connection.getAutoCommit();
    }
}
