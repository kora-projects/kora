package io.koraframework.logging.common.arg;

import io.koraframework.json.common.JsonModule;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.io.SegmentedStringWriter;

public interface StructuredArgumentWriter {
    void writeTo(JsonGenerator generator);

    default String writeToString() {
        try (var sw = new SegmentedStringWriter(JsonModule.JSON_FACTORY._getBufferRecycler());
             var gen = JsonModule.JSON_FACTORY.createGenerator(sw)) {
            this.writeTo(gen);
            gen.flush();
            return sw.getAndClear();
        }
    }
}
