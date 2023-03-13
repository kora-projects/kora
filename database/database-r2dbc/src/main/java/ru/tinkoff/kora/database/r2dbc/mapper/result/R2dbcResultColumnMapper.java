package ru.tinkoff.kora.database.r2dbc.mapper.result;

import io.r2dbc.spi.Row;
import ru.tinkoff.kora.common.Mapping;

public interface R2dbcResultColumnMapper<T> extends Mapping.MappingFunction {

    T apply(Row row, String label);
}
