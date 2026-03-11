package io.koraframework.database.jdbc;

import java.sql.SQLException;

public class RuntimeSqlException extends RuntimeException {
    public RuntimeSqlException(SQLException cause) {
        super(cause.getMessage(), cause);
    }
}
