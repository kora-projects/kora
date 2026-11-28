package io.koraframework.logging.common.arg;

import io.koraframework.common.annotation.*;
import io.koraframework.json.common.*;
import io.koraframework.json.common.util.*;
import tools.jackson.core.*;
import tools.jackson.core.io.*;

public interface StructuredArgumentMapper<T> extends Mapping.MappingFunction {

    void write(JsonGenerator gen, T value);

    default String writeToString(T value) {
        try (var sw = new SegmentedStringWriter(BufferingJsonParser.JSON_FACTORY._getBufferRecycler());
             var gen = BufferingJsonParser.JSON_FACTORY.createGenerator(ObjectWriteContext.empty(), sw)) {
            this.write(gen, value);
            gen.flush();
            return sw.getAndClear();
        }
    }
}
