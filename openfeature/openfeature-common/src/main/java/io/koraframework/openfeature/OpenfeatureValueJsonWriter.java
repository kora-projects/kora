package io.koraframework.openfeature;

import com.fasterxml.jackson.core.JsonGenerator;
import dev.openfeature.sdk.Value;
import jakarta.annotation.Nullable;
import io.koraframework.json.common.JsonWriter;

import java.io.IOException;
import java.util.Map;

public final class OpenfeatureValueJsonWriter implements JsonWriter<Value> {

    @Override
    public void write(JsonGenerator generator, @Nullable Value value) throws IOException {
        if (value == null || value.isNull()) {
            generator.writeNull();
            return;
        }
        if (value.isBoolean()) {
            generator.writeBoolean(value.asBoolean());
        } else if (value.isString()) {
            generator.writeString(value.asString());
        } else if (value.isNumber()) {
            Object raw = value.asObject();
            if (raw instanceof Integer i) {
                generator.writeNumber(i);
            } else if (raw instanceof Double d) {
                generator.writeNumber(d);
            } else {
                generator.writeNumber(((Number) raw).doubleValue());
            }
        } else if (value.isInstant()) {
            generator.writeString(value.asInstant().toString());
        } else if (value.isList()) {
            generator.writeStartArray();
            for (Value element : value.asList()) {
                write(generator, element);
            }
            generator.writeEndArray();
        } else if (value.isStructure()) {
            generator.writeStartObject();
            for (Map.Entry<String, Value> entry : value.asStructure().asMap().entrySet()) {
                generator.writeFieldName(entry.getKey());
                write(generator, entry.getValue());
            }
            generator.writeEndObject();
        } else {
            throw new IOException("Unsupported OpenFeature Value: " + value.asObject());
        }
    }
}
