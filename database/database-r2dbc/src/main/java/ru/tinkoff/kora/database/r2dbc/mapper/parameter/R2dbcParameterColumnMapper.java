package ru.tinkoff.kora.database.r2dbc.mapper.parameter;

import io.r2dbc.spi.Statement;
import ru.tinkoff.kora.common.Mapping;

public interface R2dbcParameterColumnMapper<T> extends Mapping.MappingFunction {
    void apply(Statement stmt, int index, T o);
}
