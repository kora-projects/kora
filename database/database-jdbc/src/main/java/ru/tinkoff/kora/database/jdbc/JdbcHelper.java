package ru.tinkoff.kora.database.jdbc;

import java.sql.SQLException;

public class JdbcHelper {
    public interface SqlFunction0<T> {
        T apply() throws SQLException;
    }

    public interface SqlFunction1<T, R> {
        R apply(T t) throws SQLException;
    }

    public interface SqlConsumer<T> {
        void accept(T t) throws SQLException;
    }

    public interface SqlRunnable {
        void run() throws SQLException;
    }
}
