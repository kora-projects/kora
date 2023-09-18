package ru.tinkoff.kora.database.jdbc.mapper.result;

import ru.tinkoff.kora.common.Mapping;

import jakarta.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;

public interface JdbcResultColumnMapper<T> extends Mapping.MappingFunction {
    @Nullable
    T apply(ResultSet row, int index) throws SQLException;
}
