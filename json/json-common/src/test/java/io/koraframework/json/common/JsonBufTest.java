package io.koraframework.json.common;

import org.junit.jupiter.api.Test;
import io.koraframework.json.common.util.BufferingJsonParser;
import tools.jackson.core.JsonToken;

import java.math.BigInteger;
import tools.jackson.core.JsonParser;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonBufTest {
    @Test
    void testBufferingWithNumbers() throws Exception {
        var json = """
            {
              "f1": 1,
              "f2": -2,
              "f3": 3.0,
              "f4": -4.0,
              "f5": 500000000000000000000000000000000000000000000
            }
            """;
        var p = JsonModule.JSON_FACTORY.createParser(json);
        p.nextToken();
        var bufferingParser = new BufferingJsonParser(p);
        while (true) {
            var token = bufferingParser.nextToken();
            if (token == null) {
                break;
            }
        }
        var buffered = bufferingParser.reset();
        assertThat(buffered.nextToken()).isEqualTo(JsonToken.START_OBJECT);
        assertThat(buffered.nextToken()).isEqualTo(JsonToken.PROPERTY_NAME);
        assertThat(buffered.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_INT);
        assertThat(buffered.getValueAsInt()).isEqualTo(1);
        assertThat(buffered.nextToken()).isEqualTo(JsonToken.PROPERTY_NAME);
        assertThat(buffered.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_INT);
        assertThat(buffered.getValueAsInt()).isEqualTo(-2);
        assertThat(buffered.nextToken()).isEqualTo(JsonToken.PROPERTY_NAME);
        assertThat(buffered.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_FLOAT);
        assertThat(buffered.getDoubleValue()).isEqualTo(3.0);
        assertThat(buffered.nextToken()).isEqualTo(JsonToken.PROPERTY_NAME);
        assertThat(buffered.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_FLOAT);
        assertThat(buffered.getDoubleValue()).isEqualTo(-4.0);
        assertThat(buffered.nextToken()).isEqualTo(JsonToken.PROPERTY_NAME);
        assertThat(buffered.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_INT);
        assertThat(buffered.getNumberValue()).isEqualTo(new BigInteger("500000000000000000000000000000000000000000000"));
        assertThat(buffered.nextToken()).isEqualTo(JsonToken.END_OBJECT);
    }

    @Test
    void testBufferingWithNegativeIntegers() throws Exception {
        var json = """
            [%d, -123456789, %d, -123456789012345678]
            """.formatted(Integer.MIN_VALUE, Long.MIN_VALUE);
        var buffered = replay(json);
        assertThat(buffered.nextToken()).isEqualTo(JsonToken.START_ARRAY);
        assertThat(buffered.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_INT);
        assertThat(buffered.getIntValue()).isEqualTo(Integer.MIN_VALUE);
        assertThat(buffered.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_INT);
        assertThat(buffered.getIntValue()).isEqualTo(-123456789);
        assertThat(buffered.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_INT);
        assertThat(buffered.getLongValue()).isEqualTo(Long.MIN_VALUE);
        assertThat(buffered.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_INT);
        assertThat(buffered.getLongValue()).isEqualTo(-123456789012345678L);
        assertThat(buffered.nextToken()).isEqualTo(JsonToken.END_ARRAY);
    }

    @Test
    void testBufferingWithSeveralBinaryValues() throws Exception {
        var json = """
            {"first": "AQID", "second": "BAUG"}
            """;
        var buffered = replay(json);
        assertThat(buffered.nextToken()).isEqualTo(JsonToken.START_OBJECT);
        assertThat(buffered.nextToken()).isEqualTo(JsonToken.PROPERTY_NAME);
        assertThat(buffered.nextToken()).isEqualTo(JsonToken.VALUE_STRING);
        assertThat(buffered.getBinaryValue()).containsExactly(1, 2, 3);
        assertThat(buffered.nextToken()).isEqualTo(JsonToken.PROPERTY_NAME);
        assertThat(buffered.nextToken()).isEqualTo(JsonToken.VALUE_STRING);
        assertThat(buffered.getBinaryValue()).containsExactly(4, 5, 6);
        assertThat(buffered.nextToken()).isEqualTo(JsonToken.END_OBJECT);
    }

    private static JsonParser replay(String json) {
        var p = JsonModule.JSON_FACTORY.createParser(json);
        p.nextToken();
        var bufferingParser = new BufferingJsonParser(p);
        while (bufferingParser.nextToken() != null) {
        }
        return bufferingParser.reset();
    }
}
