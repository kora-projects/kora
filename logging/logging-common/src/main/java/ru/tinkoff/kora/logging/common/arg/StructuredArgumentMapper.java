package ru.tinkoff.kora.logging.common.arg;

import ru.tinkoff.kora.common.Mapping;
import tools.jackson.core.JsonGenerator;

public interface StructuredArgumentMapper<T> extends Mapping.MappingFunction {
    void write(JsonGenerator gen, T value);
}
