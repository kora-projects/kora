package ru.tinkoff.kora.database.jdbc.mapper.parameter;

import ru.tinkoff.kora.common.Mapping;

import javax.annotation.Nullable;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public interface JdbcParameterColumnMapper<T> extends Mapping.MappingFunction {
    void set(PreparedStatement stmt, int index, @Nullable T value) throws SQLException;
}
