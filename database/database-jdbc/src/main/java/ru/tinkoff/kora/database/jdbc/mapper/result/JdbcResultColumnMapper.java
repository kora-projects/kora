package ru.tinkoff.kora.database.jdbc.mapper.result;

import ru.tinkoff.kora.common.Mapping;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;

public interface JdbcResultColumnMapper<T> extends Mapping.MappingFunction {
    @Nullable
    T apply(ResultSet rs, int index) throws SQLException;
}
