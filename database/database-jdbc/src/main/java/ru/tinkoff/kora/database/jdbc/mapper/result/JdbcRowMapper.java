package ru.tinkoff.kora.database.jdbc.mapper.result;

import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.database.common.RowMapper;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;

public interface JdbcRowMapper<T> extends Mapping.MappingFunction, RowMapper<T> {
    @Nullable
    T apply(ResultSet row) throws SQLException;
}
