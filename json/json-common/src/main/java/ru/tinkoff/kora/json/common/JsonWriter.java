package ru.tinkoff.kora.json.common;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SegmentedStringWriter;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

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

    default byte[] toByteArray(@Nullable T value) throws IOException {
        var bb = new ByteArrayBuilder(JsonCommonModule.JSON_FACTORY._getBufferRecycler());
        try (var gen = JsonCommonModule.JSON_FACTORY.createGenerator(bb, JsonEncoding.UTF8)) {
            gen.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
            this.write(gen, value);
            gen.flush();
            return bb.toByteArray();
        } finally {
            bb.release();
        }
    }

    default byte[] toByteArrayUnchecked(@Nullable T value) throws UncheckedIOException {
        try {
            return toByteArray(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    default String toString(@Nullable T value) throws IOException {
        try (var sw = new SegmentedStringWriter(JsonCommonModule.JSON_FACTORY._getBufferRecycler());
             var gen = JsonCommonModule.JSON_FACTORY.createGenerator(sw)) {
            gen.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
            this.write(gen, value);
            gen.flush();
            return sw.getAndClear();
        }
    }

    default String toStringUnchecked(@Nullable T value) throws UncheckedIOException {
        try {
            return toString(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
