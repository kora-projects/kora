package ru.tinkoff.kora.database.jdbc;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Context;

public class ConnectionContext {

    private static final Context.Key<ConnectionContext> KEY = new Context.Key<>() {
        @Override
        protected ConnectionContext copy(ConnectionContext object) {
            return null;
        }
    };

    private final Connection connection;

    private Collection<PostCommitAction> postCommitActions;

    private Collection<PostRollbackAction> postRollbackActions;

    public ConnectionContext(Connection connection) {
        this.connection = connection;
    }

    public Connection connection() {
        return this.connection;
    }

    public void addPostCommitAction(PostCommitAction action) {
        if (this.postCommitActions == null) {
            this.postCommitActions = new ArrayList<>();
        }
        this.postCommitActions.add(action);
    }

    public Collection<PostCommitAction> postCommitActions() {
        return Objects.requireNonNullElseGet(this.postCommitActions, ArrayList::new);
    }

    public void addPostRollbackAction(PostRollbackAction action) {
        if (this.postRollbackActions == null) {
            this.postRollbackActions = new ArrayList<>();
        }
        this.postRollbackActions.add(action);
    }

    public Collection<PostRollbackAction> postRollbackActions() {
        return Objects.requireNonNullElseGet(this.postRollbackActions, ArrayList::new);
    }

    @Nullable
    public static ConnectionContext get(Context context) {
        return context.get(KEY);
    }

    public static ConnectionContext set(Context context, ConnectionContext connectionContext) {
        return context.set(KEY, connectionContext);
    }

    public static void remove(Context context) {
        context.remove(KEY);
    }

    public interface PostCommitAction {
        void run();
    }

    public interface PostRollbackAction {
        void run();
    }
}
