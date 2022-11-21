package ru.tinkoff.kora.database.jdbc.mapper.result;

import ru.tinkoff.kora.common.Mapping;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public interface JdbcResultStatementMapper<T> extends Mapping.MappingFunction {
    T apply(PreparedStatement stmt) throws SQLException;
}
