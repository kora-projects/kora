package ru.tinkoff.kora.json.common;

import com.fasterxml.jackson.core.*;

import javax.annotation.Nullable;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class JsonObjectCodec {
    public static Object parse(JsonParser parser) throws IOException {
        var token = parser.currentToken();
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token.isScalarValue()) {
            if (token == JsonToken.VALUE_TRUE) {
                return true;
            }
            if (token == JsonToken.VALUE_FALSE) {
                return false;
            }
            if (token == JsonToken.VALUE_STRING) {
                return parser.getText();
            }
            if (token == JsonToken.VALUE_NUMBER_INT) {
                return parser.getBigIntegerValue();
            }
            if (token == JsonToken.VALUE_NUMBER_FLOAT) {
                return parser.getDoubleValue();
            }
            throw new JsonParseException(parser, "Expecting {VALUE_TRUE, VALUE_FALSE, VALUE_STRING, VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT} token, got " + token);
        }
        if (token == JsonToken.START_OBJECT) {
            var object = new HashMap<String, Object>();
            String fieldName;
            while ((fieldName = parser.nextFieldName()) != null) {
                parser.nextToken();
                var value = parse(parser);
                object.put(fieldName, value);
            }
            return object;
        }
        if (token == JsonToken.START_ARRAY) {
            var object = new ArrayList<>();
            while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
                var value = parse(parser);
                object.add(value);
            }
            return object;
        }
        throw new JsonParseException(parser, "Unexpected token " + token);
    }

    public static void write(JsonGenerator gen, @Nullable Object object) throws IOException {
        if (object == null) {
            gen.writeNull();
            return;
        }
        if (object instanceof String str) {
            gen.writeString(str);
            return;
        }
        if (object instanceof Integer i) {
            gen.writeNumber(i);
            return;
        }
        if (object instanceof Boolean b) {
            gen.writeBoolean(b);
            return;
        }
        if (object instanceof Long l) {
            gen.writeNumber(l);
            return;
        }
        if (object instanceof Double d) {
            gen.writeNumber(d);
            return;
        }
        if (object instanceof Float f) {
            gen.writeNumber(f);
            return;
        }
        if (object instanceof BigDecimal bd) {
            gen.writeNumber(bd);
            return;
        }
        if (object instanceof BigInteger bi) {
            gen.writeNumber(bi);
            return;
        }
        if (object instanceof OffsetDateTime dt) {
            gen.writeString(dt.toString());
            return;
        }
        if (object instanceof LocalDate date) {
            gen.writeString(date.toString());
            return;
        }
        if (object instanceof Enum<?> e) {
            gen.writeString(e.name());
            return;
        }
        if (object instanceof byte[] bytes) {
            gen.writeBinary(bytes);
            return;
        }
        if (object instanceof UUID uuid) {
            gen.writeString(uuid.toString());
            return;
        }
        if (object instanceof RawJson rawJson) {
            gen.writeRawValue(rawJson);
            return;
        }
        if (object instanceof Map<?, ?> map) {
            gen.writeStartObject(map);
            for (var entry : map.entrySet()) {
                var key = entry.getKey();
                if (key instanceof String str) {
                    gen.writeFieldName(str);
                } else if (key instanceof SerializableString str) {
                    gen.writeFieldName(str);
                } else {
                    throw new JsonGenerationException("Maps key should be strings", gen);
                }
                var value = entry.getValue();
                write(gen, value);
            }
            gen.writeEndObject();
            return;
        }
        if (object instanceof Iterable<?> iterable) {
            gen.writeStartArray(iterable);
            for (var o : iterable) {
                write(gen, o);
            }
            gen.writeEndArray();
            return;
        }
        throw new IllegalStateException("Invalid type " + object.getClass() + ". Valid types are T: Integer, Long, String, Double, Float, Biginteger, BigDecimal, OffsetDateTime, LocalDateTime, Enum, byte[], RawJson, UUID, Map<String, T>, Iterable<T>");
    }
}
