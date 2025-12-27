package ru.tinkoff.kora.json.common;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonEncoding;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.PrettyPrinter;
import tools.jackson.core.io.SegmentedStringWriter;
import tools.jackson.core.util.ByteArrayBuilder;
import tools.jackson.core.util.DefaultPrettyPrinter;

/**
 * <b>Русский</b>: Контракт писателя JSON со всеми методами записи
 * <hr>
 * <b>English</b>: JSON writer contract with all write methods
 */
public interface JsonWriter<T> {

    /**
     * @param generator jackson generator that will be used for writing object to JSON
     * @param object    to serialize into JSON
     */
    void write(JsonGenerator generator, @Nullable T object);

    default byte[] toByteArray(@Nullable T value) {
        var bb = new ByteArrayBuilder(JsonCommonModule.JSON_FACTORY._getBufferRecycler());
        try (var gen = JsonCommonModule.JSON_FACTORY.createGenerator(ObjectWriteContext.empty(), bb, JsonEncoding.UTF8)) {
            this.write(gen, value);
            gen.flush();
            return bb.toByteArray();
        } finally {
            bb.release();
        }
    }

    default String toString(@Nullable T value) {
        return toString(value, false);
    }

    default String toPrettyString(@Nullable T value) {
        return toString(value, true);
    }

    private String toString(@Nullable T value, boolean usePrettyPrinter) {
        var ctx = usePrettyPrinter
            ? new ObjectWriteContext.Base() {
            @Override
            public PrettyPrinter getPrettyPrinter() {
                return new DefaultPrettyPrinter();
            }
        }
            : ObjectWriteContext.empty();
        try (var sw = new SegmentedStringWriter(JsonCommonModule.JSON_FACTORY._getBufferRecycler());
             var gen = JsonCommonModule.JSON_FACTORY.createGenerator(ctx, sw)) {
            this.write(gen, value);
            gen.flush();
            return sw.getAndClear();
        }
    }
}
