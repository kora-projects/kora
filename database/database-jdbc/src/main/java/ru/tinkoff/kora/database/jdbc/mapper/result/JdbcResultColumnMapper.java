package ru.tinkoff.kora.database.jdbc.mapper.result;

import ru.tinkoff.kora.common.Mapping;

import java.sql.ResultSet;
import java.sql.SQLException;

public interface JdbcResultColumnMapper<T> extends Mapping.MappingFunction {
    T apply(ResultSet rs, int index) throws SQLException;
}
