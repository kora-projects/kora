package ru.tinkoff.kora.database.jdbc;

import jakarta.annotation.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class ConnectionContext {

    private static final ScopedValue<ConnectionContext> VALUE = ScopedValue.newInstance();

    private final Connection connection;

    private List<PostCommitAction> postCommitActions;

    private List<PostRollbackAction> postRollbackActions;

    public ConnectionContext(Connection connection) {
        this.connection = connection;
    }

    public Connection connection() {
        return this.connection;
    }

    public void addPostCommitAction(PostCommitAction action) throws SQLException {
        if (!this.isActiveTransaction()) {
            throw new IllegalStateException("Cannot add post commit action when transaction is not active");
        }
        if (this.postCommitActions == null) {
            this.postCommitActions = new ArrayList<>();
        }
        this.postCommitActions.add(action);
    }

    public Collection<PostCommitAction> postCommitActions() {
        return Objects.requireNonNullElseGet(this.postCommitActions, List::of);
    }

    public void addPostRollbackAction(PostRollbackAction action) throws SQLException {
        if (!this.isActiveTransaction()) {
            throw new IllegalStateException("Cannot add post rollback action when transaction is not active");
        }
        if (this.postRollbackActions == null) {
            this.postRollbackActions = new ArrayList<>();
        }
        this.postRollbackActions.add(action);
    }

    public Collection<PostRollbackAction> postRollbackActions() {
        return Objects.requireNonNullElseGet(this.postRollbackActions, List::of);
    }

    @Nullable
    public static ConnectionContext get() {
        if (VALUE.isBound()) {
            return VALUE.get();
        } else {
            return null;
        }
    }

    static <T, X extends Throwable> T with(ConnectionContext ctx, ScopedValue.CallableOp<T, X> op) throws X {
        return ScopedValue.where(VALUE, ctx).call(op);
    }

    public interface PostCommitAction {
        void run(Connection connection) throws SQLException;
    }

    public interface PostRollbackAction {
        void run(Connection connection, Exception e) throws SQLException;
    }

    private boolean isActiveTransaction() throws SQLException {
        return this.connection != null && !this.connection.isClosed() && !this.connection.getAutoCommit();
    }
}
