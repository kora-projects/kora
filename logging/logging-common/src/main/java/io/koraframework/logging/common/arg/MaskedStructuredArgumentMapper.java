package io.koraframework.logging.common.arg;

import io.koraframework.json.common.JsonWriter;
import io.koraframework.logging.common.masking.MaskingJsonGenerator;
import io.koraframework.logging.common.masking.MaskingMetadata;
import tools.jackson.core.JsonGenerator;

public final class MaskedStructuredArgumentMapper<T> implements StructuredArgumentMapper<T> {
    private final JsonWriter<T> writer;
    private final MaskingMetadata<T> metadata;

    public MaskedStructuredArgumentMapper(JsonWriter<T> writer, MaskingMetadata<T> metadata) {
        this.writer = writer;
        this.metadata = metadata;
    }

    @Override
    public void write(JsonGenerator gen, T value) {
        this.writer.write(new MaskingJsonGenerator(gen, this.metadata), value);
    }
}
