package io.koraframework.openfeature.mapper;

import dev.openfeature.sdk.Value;
import io.koraframework.json.common.JsonWriter;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonGenerator;

public final class OpenfeatureValueJsonWriter implements JsonWriter<Value> {
    @Override
    public void write(JsonGenerator generator, @Nullable Value value) {
        if (value == null || value.isNull()) {
            generator.writeNull();
        } else if (value.isBoolean()) {
            generator.writeBoolean(value.asBoolean());
        } else if (value.isString()) {
            generator.writeString(value.asString());
        } else if (value.isNumber()) {
            switch (value.asObject()) {
                case Integer i -> generator.writeNumber(i);
                case Long l -> generator.writeNumber(l);
                case Double d -> generator.writeNumber(d);
                default -> throw new IllegalArgumentException("Unsupported OpenFeature number: " + value.asObject());
            }
        } else if (value.isInstant()) {
            generator.writeString(value.asInstant().toString());
        } else if (value.isList()) {
            generator.writeStartArray();
            for (var element : value.asList()) {
                write(generator, element);
            }
            generator.writeEndArray();
        } else if (value.isStructure()) {
            generator.writeStartObject();
            for (var entry : value.asStructure().asMap().entrySet()) {
                generator.writeName(entry.getKey());
                write(generator, entry.getValue());
            }
            generator.writeEndObject();
        } else {
            throw new IllegalArgumentException("Unsupported OpenFeature Value: " + value.asObject());
        }
    }
}
