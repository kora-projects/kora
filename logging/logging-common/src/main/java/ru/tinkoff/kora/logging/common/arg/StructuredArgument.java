package ru.tinkoff.kora.logging.common.arg;

import com.fasterxml.jackson.core.JsonGenerator;
import org.slf4j.Marker;
import ru.tinkoff.kora.json.common.JsonWriter;

import javax.annotation.Nullable;
import java.util.Map;

public interface StructuredArgument extends StructuredArgumentWriter {
    String fieldName();

    static <T> StructuredArgument arg(String fieldName, @Nullable T value, JsonWriter<T> writer) {
        return new ArgumentWithValueAndWriter<>(fieldName, value, writer);
    }

    static StructuredArgument arg(String fieldName, StructuredArgumentWriter writer) {
        return new ArgumentWithWriter(fieldName, writer);
    }

    static StructuredArgument arg(String fieldName, String value) {
        return new ArgumentWithValueAndWriter<>(fieldName, value, JsonGenerator::writeString);
    }

    static StructuredArgument arg(String fieldName, Integer value) {
        return new ArgumentWithValueAndWriter<>(fieldName, value, JsonGenerator::writeNumber);
    }

    static StructuredArgument arg(String fieldName, Long value) {
        return new ArgumentWithValueAndWriter<>(fieldName, value, JsonGenerator::writeNumber);
    }

    static StructuredArgument arg(String fieldName, Boolean value) {
        return new ArgumentWithValueAndWriter<>(fieldName, value, JsonGenerator::writeBoolean);
    }

    static StructuredArgument arg(String fieldName, Map<String, String> value) {
        return new ArgumentWithValueAndWriter<>(fieldName, value, (gen, object) -> {
            gen.writeStartObject(object);
            for (var entry : object.entrySet()) {
                gen.writeFieldName(entry.getKey());
                gen.writeString(entry.getValue());
            }
            gen.writeEndObject();
        });
    }

    static <T> Marker marker(String fieldName, @Nullable T value, JsonWriter<T> writer) {
        return new ArgumentMarkerWithValueAndWriter<>(fieldName, value, writer);
    }

    static Marker marker(String fieldName, StructuredArgumentWriter writer) {
        return new ArgumentMarkerWithWriter(fieldName, writer);
    }

    static Marker marker(String fieldName, String value) {
        return new ArgumentMarkerWithValueAndWriter<>(fieldName, value, JsonGenerator::writeString);
    }

    static Marker marker(String fieldName, Integer value) {
        return new ArgumentMarkerWithValueAndWriter<>(fieldName, value, JsonGenerator::writeNumber);
    }

    static Marker marker(String fieldName, Long value) {
        return new ArgumentMarkerWithValueAndWriter<>(fieldName, value, JsonGenerator::writeNumber);
    }

    static Marker marker(String fieldName, Boolean value) {
        return new ArgumentMarkerWithValueAndWriter<>(fieldName, value, JsonGenerator::writeBoolean);
    }

    static Marker marker(String fieldName, Map<String, String> value) {
        return new ArgumentMarkerWithValueAndWriter<>(fieldName, value, (gen, object) -> {
            gen.writeStartObject(object);
            for (var entry : object.entrySet()) {
                gen.writeFieldName(entry.getKey());
                gen.writeString(entry.getValue());
            }
            gen.writeEndObject();
        });
    }
}
