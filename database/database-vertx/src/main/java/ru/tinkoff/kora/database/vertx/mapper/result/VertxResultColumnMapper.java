package ru.tinkoff.kora.database.vertx.mapper.result;

import io.vertx.sqlclient.Row;
import ru.tinkoff.kora.common.Mapping;

public interface VertxResultColumnMapper<T> extends Mapping.MappingFunction {
    T apply(Row row, int index);
}
