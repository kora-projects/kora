package ru.tinkoff.kora.database.vertx.mapper.parameter;

import ru.tinkoff.kora.common.Mapping;

public interface VertxParameterColumnMapper<T> extends Mapping.MappingFunction {
    Object apply(T t);
}
