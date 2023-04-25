package ru.tinkoff.kora.database.r2dbc.mapper.result;

import io.r2dbc.spi.Row;
import ru.tinkoff.kora.common.Mapping;

import javax.annotation.Nullable;

public interface R2dbcResultColumnMapper<T> extends Mapping.MappingFunction {
    @Nullable
    T apply(Row row, String label);
}
