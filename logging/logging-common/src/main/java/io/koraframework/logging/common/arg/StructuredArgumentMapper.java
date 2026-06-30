package io.koraframework.logging.common.arg;

import io.koraframework.common.annotation.Mapping;
import tools.jackson.core.JsonGenerator;

public interface StructuredArgumentMapper<T> extends Mapping.MappingFunction {
    void write(JsonGenerator gen, T value);
}
