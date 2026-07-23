package io.koraframework.logging.common.arg;

import io.koraframework.json.common.JsonWriter;
import io.koraframework.json.common.JsonModule;
import io.koraframework.logging.common.masking.MaskingJsonGenerator;
import io.koraframework.logging.common.masking.MaskingRules;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.io.SegmentedStringWriter;

public class MaskedStructuredArgumentMapper<T> implements StructuredArgumentMapper<T> {

    private final JsonWriter<T> writer;
    private final MaskingRules<T> rules;
    private final boolean structured;

    public MaskedStructuredArgumentMapper(JsonWriter<T> writer, MaskingRules<T> rules) {
        this(writer, rules, true);
    }

    public MaskedStructuredArgumentMapper(JsonWriter<T> writer, MaskingRules<T> rules, boolean structured) {
        this.writer = writer;
        this.rules = rules;
        this.structured = structured;
    }

    @Override
    public void write(JsonGenerator gen, T value) {
        if (this.structured) {
            this.writer.write(new MaskingJsonGenerator(gen, this.rules), value);
            return;
        }
        gen.writeString(this.writeMaskedAsString(value));
    }

    private String writeMaskedAsString(T value) {
        try (var sw = new SegmentedStringWriter(JsonModule.JSON_FACTORY._getBufferRecycler());
             var gen = JsonModule.JSON_FACTORY.createGenerator(sw)) {
            this.writer.write(new MaskingJsonGenerator(gen, this.rules), value);
            gen.flush();
            return sw.getAndClear();
        }
    }
}
