package ru.tinkoff.kora.database.vertx.mapper.result;

import io.vertx.sqlclient.Row;
import ru.tinkoff.kora.common.Mapping;

import javax.annotation.Nullable;

public interface VertxResultColumnMapper<T> extends Mapping.MappingFunction {
    @Nullable
    T apply(Row row, int index);
}
