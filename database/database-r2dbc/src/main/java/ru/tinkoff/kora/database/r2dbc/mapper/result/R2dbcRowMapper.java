package ru.tinkoff.kora.database.r2dbc.mapper.result;

import io.r2dbc.spi.Row;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.database.common.RowMapper;

import javax.annotation.Nullable;

public interface R2dbcRowMapper<T> extends Mapping.MappingFunction, RowMapper<T> {
    @Nullable
    T apply(Row row);
}
