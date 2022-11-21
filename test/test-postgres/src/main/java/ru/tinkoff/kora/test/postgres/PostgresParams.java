package ru.tinkoff.kora.test.postgres;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

public record PostgresParams(String host, int port, String db, String user, String password) {
    public String jdbcUrl() {
        return "jdbc:postgresql://%s:%d/%s".formatted(host, port, db);
    }

    public Connection createConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl(), user, password);
    }

    public PostgresParams withDb(String db) {
        return new PostgresParams(host, port, db, user, password);
    }


    public void execute(String sql) {
        try (var connection = createConnection();
             var stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public interface ResultSetMapper<R, E extends Throwable> {
        R apply(ResultSet rs) throws SQLException, E;
    }

    public <T, E extends Throwable> T query(String sql, ResultSetMapper<T, E> extractor) throws E {
        try (var connection = createConnection();
             var stmt = connection.prepareStatement(sql);
             var rs = stmt.executeQuery()) {
            return extractor.apply(rs);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
