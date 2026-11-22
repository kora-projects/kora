package io.koraframework.logging.common.arg;

import io.koraframework.json.common.*;
import io.koraframework.json.common.util.*;
import tools.jackson.core.*;
import tools.jackson.core.io.*;

public interface StructuredArgumentWriter {

    void writeTo(JsonGenerator generator);

    default String writeToString() {
        try (var sw = new SegmentedStringWriter(BufferingJsonParser.JSON_FACTORY._getBufferRecycler());
             var gen = BufferingJsonParser.JSON_FACTORY.createGenerator(ObjectWriteContext.empty(), sw)) {
            this.writeTo(gen);
            gen.flush();
            return sw.getAndClear();
        }
    }
}
