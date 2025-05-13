package ru.tinkoff.kora.database.jdbc;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;

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

    private final Collection<PostCommitAction> postCommitActions = new ArrayList<>();

    public ConnectionContext(Connection connection) {
        this.connection = connection;
    }

    public Connection connection() {
        return this.connection;
    }

    public void addPostCommitAction(PostCommitAction action) {
        this.postCommitActions.add(action);
    }

    public Collection<PostCommitAction> postCommitActions() {
        return this.postCommitActions;
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
}
