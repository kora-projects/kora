package io.koraframework.database.jdbc.exception;

import java.sql.SQLException;

public class UncheckedSqlException extends RuntimeException {

    public UncheckedSqlException(SQLException cause) {
        super(cause.getMessage(), cause);
    }
}
