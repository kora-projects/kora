package ru.tinkoff.kora.json.common;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SegmentedStringWriter;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * <b>Русский</b>: Контракт писателя JSON со всеми методами записи
 * <hr>
 * <b>English</b>: JSON writer contract with all write methods
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
        return toString(value, false);
    }

    default String toStringUnchecked(@Nullable T value) throws UncheckedIOException {
        return toStringUnchecked(value, false);
    }

    default String toPrettyString(@Nullable T value) throws IOException {
        return toString(value, true);
    }

    default String toPrettyStringUnchecked(@Nullable T value) throws UncheckedIOException {
        return toStringUnchecked(value, true);
    }

    private String toString(@Nullable T value, boolean usePrettyPrinter) throws IOException {
        try (var sw = new SegmentedStringWriter(JsonCommonModule.JSON_FACTORY._getBufferRecycler());
             var gen = JsonCommonModule.JSON_FACTORY.createGenerator(sw)) {
            gen.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
            if (usePrettyPrinter) gen.useDefaultPrettyPrinter();
            this.write(gen, value);
            gen.flush();
            return sw.getAndClear();
        }
    }

    private String toStringUnchecked(@Nullable T value, boolean usePrettyPrinter) throws UncheckedIOException {
        try {
            return toString(value, usePrettyPrinter);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
