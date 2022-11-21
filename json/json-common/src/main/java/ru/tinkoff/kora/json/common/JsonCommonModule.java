package ru.tinkoff.kora.json.common;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;
import ru.tinkoff.kora.common.DefaultComponent;

import java.time.LocalDate;
import java.util.*;

public interface JsonCommonModule {
    JsonFactory JSON_FACTORY = new JsonFactory(new JsonFactoryBuilder().disable(JsonFactory.Feature.INTERN_FIELD_NAMES));

    @DefaultComponent
    default JsonWriter<Object> objectJsonWriter() {
        return JsonObjectCodec::write;
    }

    @DefaultComponent
    default JsonReader<Object> objectJsonReader() {
        return JsonObjectCodec::parse;
    }

    default <T> JsonWriter<List<T>> listJsonWriterFactory(JsonWriter<T> writer) {
        return new ListJsonWriter<>(writer);
    }

    default <T> JsonReader<List<T>> listJsonReaderFactory(JsonReader<T> reader) {
        return new ListJsonReader<>(reader);
    }

    default <T> JsonWriter<Map<String, T>> mapJsonWriterFactory(JsonWriter<T> writer) {
        return new MapJsonWriter<>(writer);
    }

    default <T> JsonReader<Map<String, T>> mapJsonReaderFactory(JsonReader<T> reader) {
        return new MapJsonReader<>(reader);
    }

    default <T> JsonWriter<Set<T>> setJsonWriterFactory(JsonWriter<T> writer) {
        return new SetJsonWriter<>(writer);
    }

    default <T> JsonReader<Set<T>> setJsonReaderFactory(JsonReader<T> reader) {
        return new SetJsonReader<>(reader);
    }

    default <T extends Comparable<T>> JsonReader<SortedSet<T>> sortedSetJsonReaderFactory(JsonReader<T> reader) {
        return new SortedSetJsonReader<>(reader);
    }

    default JsonWriter<Integer> integerJsonWriter() {
        return (gen, object) -> {
            if (object == null) {
                gen.writeNull();
            } else {
                gen.writeNumber(object);
            }
        };
    }

    default JsonReader<Integer> integerJsonReader() {
        return parser -> {
            var token = parser.currentToken();
            if (token == JsonToken.VALUE_NULL) {
                return null;
            }
            if (token != JsonToken.VALUE_NUMBER_INT) {
                throw new JsonParseException(parser, "Expecting VALUE_NUMBER_INT token, got " + token);
            }
            return parser.getIntValue();
        };
    }

    default JsonWriter<LocalDate> localDateJsonWriter() {
        return (gen, object) -> {
            if (object == null) {
                gen.writeNull();
            } else {
                gen.writeString(object.toString());
            }
        };
    }

    default JsonReader<LocalDate> localDateJsonReader() {
        return parser -> {
            var token = parser.currentToken();
            if (token == JsonToken.VALUE_NULL) {
                return null;
            }
            if (token != JsonToken.VALUE_STRING) {
                throw new JsonParseException(parser, "Expecting VALUE_STRING token, got " + token);
            }
            return LocalDate.parse(parser.getValueAsString());
        };
    }

    default JsonWriter<Long> longJsonWriter() {
        return (gen, object) -> {
            if (object == null) {
                gen.writeNull();
            } else {
                gen.writeNumber(object);
            }
        };
    }

    default JsonReader<Long> longJsonReader() {
        return parser -> {
            var token = parser.currentToken();
            if (token == JsonToken.VALUE_NULL) {
                return null;
            }
            if (token != JsonToken.VALUE_NUMBER_INT) {
                throw new JsonParseException(parser, "Expecting VALUE_NUMBER_INT token, got " + token);
            }
            return parser.getLongValue();
        };
    }

    default JsonWriter<Double> doubleJsonWriter() {
        return (gen, object) -> {
            if (object == null) {
                gen.writeNull();
            } else {
                gen.writeNumber(object);
            }
        };
    }

    default JsonReader<Double> doubleJsonReader() {
        return parser -> {
            var token = parser.currentToken();
            if (token == JsonToken.VALUE_NULL) {
                return null;
            }
            if (token != JsonToken.VALUE_NUMBER_FLOAT) {
                throw new JsonParseException(parser, "Expecting VALUE_NUMBER_FLOAT token, got " + token);
            }
            return parser.getDoubleValue();
        };
    }

    default JsonWriter<String> stringJsonWriter() {
        return (gen, object) -> {
            if (object == null) {
                gen.writeNull();
            } else {
                gen.writeString(object);
            }
        };
    }

    default JsonReader<String> stringJsonReader() {
        return parser -> {
            var token = parser.currentToken();
            if (token == JsonToken.VALUE_NULL) {
                return null;
            }
            if (token != JsonToken.VALUE_STRING) {
                throw new JsonParseException(parser, "Expecting VALUE_STRING token, got " + token);
            }
            return parser.getText();
        };
    }

    default JsonWriter<Boolean> booleanJsonWriter() {
        return (gen, object) -> {
            if (object == null) {
                gen.writeNull();
            } else {
                gen.writeBoolean(object);
            }
        };
    }

    default JsonReader<Boolean> booleanJsonReader() {
        return parser -> {
            var token = parser.currentToken();
            if (token == JsonToken.VALUE_NULL) {
                return null;
            }
            if (token == JsonToken.VALUE_TRUE) {
                return true;
            }
            if (token == JsonToken.VALUE_FALSE) {
                return false;
            }
            throw new JsonParseException(parser, "Expecting VALUE_TRUE or VALUE_FALSE token, got " + token);
        };
    }

    default JsonWriter<RawJson> rawJsonWriter() {
        return new RawJsonWriter();
    }

    default JsonReader<UUID> uuidJsonReader() {
        return new UuidJsonCodec();
    }

    default JsonWriter<UUID> uuidJsonWriter() {
        return new UuidJsonCodec();
    }
}
