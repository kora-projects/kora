package ru.tinkoff.kora.logging.common.arg;

import ru.tinkoff.kora.json.common.JsonCommonModule;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.io.SegmentedStringWriter;

public interface StructuredArgumentWriter {
    void writeTo(JsonGenerator generator);

    default String writeToString() {
        try (var sw = new SegmentedStringWriter(JsonCommonModule.JSON_FACTORY._getBufferRecycler());
             var gen = JsonCommonModule.JSON_FACTORY.createGenerator(sw)) {
            this.writeTo(gen);
            gen.flush();
            return sw.getAndClear();
        }
    }
}
