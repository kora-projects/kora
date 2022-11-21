package ru.tinkoff.kora.json.common;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Class that defines public API for writing JSON content.
 */
public interface JsonWriter<T> {

    /**
     * @param generator jackson generator that will be used for writing object to JSON
     * @param object to serialize into JSON
     * @throws IOException in case of serialization errors
     */
    void write(JsonGenerator generator, @Nullable T object) throws IOException;

    default byte[] toByteArray(@Nullable T object) throws IOException {
        var bb = new ByteArrayBuilder(JsonCommonModule.JSON_FACTORY._getBufferRecycler());
        try (var gen = JsonCommonModule.JSON_FACTORY.createGenerator(bb, JsonEncoding.UTF8)) {
            gen.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
            this.write(gen, object);
            gen.flush();
            return bb.toByteArray();
        } finally {
            bb.release();
        }
    }
}
