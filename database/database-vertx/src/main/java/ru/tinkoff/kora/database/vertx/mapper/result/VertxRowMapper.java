package ru.tinkoff.kora.database.vertx.mapper.result;

import io.vertx.sqlclient.Row;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.database.common.RowMapper;

public interface VertxRowMapper<T> extends Mapping.MappingFunction, RowMapper<T> {
    T apply(Row row);
}
