package ru.tinkoff.kora.logging.common.arg;

import com.fasterxml.jackson.core.JsonGenerator;
import ru.tinkoff.kora.common.Mapping;

import java.io.IOException;

public interface StructuredArgumentMapper<T> extends Mapping.MappingFunction {
    void write(JsonGenerator gen, T value) throws IOException;
}
