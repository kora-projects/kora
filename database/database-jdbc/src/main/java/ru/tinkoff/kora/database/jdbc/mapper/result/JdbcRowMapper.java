package ru.tinkoff.kora.database.jdbc.mapper.result;

import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.database.common.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public interface JdbcRowMapper<T> extends Mapping.MappingFunction, RowMapper<T> {
    T apply(ResultSet rs) throws SQLException;
}
