package io.koraframework.logging.common.arg;

import io.koraframework.common.annotation.Mapping;
import io.koraframework.json.common.JsonModule;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.io.SegmentedStringWriter;

public interface StructuredArgumentMapper<T> extends Mapping.MappingFunction {

    void write(JsonGenerator gen, T value);

    default String writeToString(T value) {
        try (var sw = new SegmentedStringWriter(JsonModule.JSON_FACTORY._getBufferRecycler());
             var gen = JsonModule.JSON_FACTORY.createGenerator(sw)) {
            this.write(gen, value);
            gen.flush();
            return sw.getAndClear();
        }
    }
}
