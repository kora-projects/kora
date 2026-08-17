package io.koraframework.json.common;

import io.koraframework.common.annotation.Mapping;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.*;
import tools.jackson.core.io.SegmentedStringWriter;
import tools.jackson.core.util.ByteArrayBuilder;
import tools.jackson.core.util.DefaultPrettyPrinter;

/**
 * <b>Русский</b>: Контракт писателя JSON со всеми методами записи
 * <hr>
 * <b>English</b>: JSON writer contract with all write methods
 */
public interface JsonWriter<T> extends Mapping.MappingFunction {

    /**
     * @param generator jackson generator that will be used for writing object to JSON
     * @param object    to serialize into JSON
     */
    void write(JsonGenerator generator, @Nullable T object) throws JacksonException;

    default byte[] toByteArray(@Nullable T value) throws JacksonException {
        var bb = new ByteArrayBuilder(JsonModule.JSON_FACTORY._getBufferRecycler());
        try (var gen = JsonModule.JSON_FACTORY.createGenerator(ObjectWriteContext.empty(), bb, JsonEncoding.UTF8)) {
            this.write(gen, value);
            gen.flush();
            return bb.toByteArray();
        } finally {
            bb.release();
        }
    }

    default String toString(@Nullable T value) throws JacksonException {
        return toString(value, false);
    }

    default String toPrettyString(@Nullable T value) throws JacksonException {
        return toString(value, true);
    }

    private String toString(@Nullable T value, boolean usePrettyPrinter) throws JacksonException {
        var ctx = usePrettyPrinter
            ? new ObjectWriteContext.Base() {
            @Override
            public PrettyPrinter getPrettyPrinter() {
                return new DefaultPrettyPrinter();
            }
        }
            : ObjectWriteContext.empty();
        try (var sw = new SegmentedStringWriter(JsonModule.JSON_FACTORY._getBufferRecycler());
             var gen = JsonModule.JSON_FACTORY.createGenerator(ctx, sw)) {
            this.write(gen, value);
            gen.flush();
            return sw.getAndClear();
        }
    }
}
