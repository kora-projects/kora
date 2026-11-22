package io.koraframework.json.common;

import com.alibaba.fastjson2.*;
import io.koraframework.common.annotation.*;
import org.jspecify.annotations.*;
import tools.jackson.core.*;

import java.io.*;

import static io.koraframework.json.common.JsonPools.*;

/**
 * <b>Русский</b>: Контракт писателя JSON со всеми методами записи
 * <hr>
 * <b>English</b>: JSON writer contract with all write methods
 */
public interface JsonWriter<T> extends Mapping.MappingFunction {
    JSONWriter.Context PRETTY_WRITER_CONTEXT = new JSONWriter.Context(JSONFactory.getDefaultObjectWriterProvider(), JSONWriter.Feature.PrettyFormat);
    byte[] NULL_BYTES = new byte[] {'n', 'u', 'l', 'l'};

    default void write(JsonGenerator gen, @Nullable T object) {
        throw new UnsupportedOperationException("This writer does not support Jackson JsonGenerator directly");
    }

    default void write(JSONWriter writer, @Nullable T object) {
        throw new UnsupportedOperationException("This writer does not support fastjson2 JSONWriter directly");
    }

    default byte[] toByteArray(@Nullable T value) {
        if (value == null) {
            return NULL_BYTES;
        }
        try (var writer = JSONWriter.ofUTF8(DEFAULT_WRITER_CONTEXT)) {
            this.write(writer, value);
            return writer.getBytes();
        }
    }

    default void writeTo(OutputStream out, @Nullable T value) throws IOException {
        if (value == null) {
            out.write(NULL_BYTES);
            return;
        }
        try (var writer = JSONWriter.ofUTF8(DEFAULT_WRITER_CONTEXT)) {
            this.write(writer, value);
            writer.flushTo(out);
        }
    }

    default String toString(@Nullable T value) {
        if (value == null) {
            return "null";
        }
        try (var writer = JSONWriter.ofUTF8(DEFAULT_WRITER_CONTEXT)) {
            this.write(writer, value);
            return writer.toString();
        }
    }

    default String toPrettyString(@Nullable T value) {
        if (value == null) {
            return "null";
        }
        try (var writer = JSONWriter.ofUTF8(PRETTY_WRITER_CONTEXT)) {
            this.write(writer, value);
            return writer.toString();
        }
    }
}
