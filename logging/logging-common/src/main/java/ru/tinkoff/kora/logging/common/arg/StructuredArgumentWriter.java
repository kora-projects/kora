package ru.tinkoff.kora.logging.common.arg;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SegmentedStringWriter;
import ru.tinkoff.kora.json.common.JsonCommonModule;

import java.io.IOException;

public interface StructuredArgumentWriter {
    void writeTo(JsonGenerator generator) throws IOException;

    default String writeToString() {
        try (var sw = new SegmentedStringWriter(JsonCommonModule.JSON_FACTORY._getBufferRecycler());
             var gen = JsonCommonModule.JSON_FACTORY.createGenerator(sw)) {
            this.writeTo(gen);
            gen.flush();
            return sw.getAndClear();
        } catch (IOException e) {
            return "<error>";
        }
    }
}
