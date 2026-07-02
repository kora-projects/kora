package io.koraframework.database.jdbc;

import java.sql.SQLException;

public class UncheckedSqlException extends RuntimeException {

    public UncheckedSqlException(SQLException cause) {
        super(cause.getMessage(), cause);
    }
}
