package ru.tinkoff.kora.database.vertx.mapper.parameter;

import ru.tinkoff.kora.common.Mapping;

import javax.annotation.Nullable;

public interface VertxParameterColumnMapper<T> extends Mapping.MappingFunction {
    Object apply(@Nullable T t);
}
