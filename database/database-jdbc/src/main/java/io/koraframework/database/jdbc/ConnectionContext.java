package io.koraframework.database.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class ConnectionContext {

    private final Connection connection;

    private List<PostCommitAction> afterCommitActions;
    private List<PostRollbackAction> afterRollbackActions;

    public ConnectionContext(Connection connection) {
        this.connection = connection;
    }

    @SuppressWarnings("resource")
    public Connection connection() {
        return this.connection;
    }

    public ConnectionContext afterCommit(PostCommitAction action) throws SQLException {
        if (!this.isActiveTransaction()) {
            throw new IllegalStateException("Cannot add post commit action when transaction is not active");
        }
        if (this.afterCommitActions == null) {
            this.afterCommitActions = new ArrayList<>();
        }
        this.afterCommitActions.add(action);
        return this;
    }

    Collection<PostCommitAction> postCommitActions() {
        return Objects.requireNonNullElseGet(this.afterCommitActions, List::of);
    }

    public ConnectionContext afterRollback(PostRollbackAction action) throws SQLException {
        if (!this.isActiveTransaction()) {
            throw new IllegalStateException("Cannot add post rollback action when transaction is not active");
        }
        if (this.afterRollbackActions == null) {
            this.afterRollbackActions = new ArrayList<>();
        }
        this.afterRollbackActions.add(action);
        return this;
    }

    Collection<PostRollbackAction> postRollbackActions() {
        return Objects.requireNonNullElseGet(this.afterRollbackActions, List::of);
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
