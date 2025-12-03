package ru.tinkoff.kora.json.common;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.json.common.util.BufferingJsonParser;
import tools.jackson.core.JsonToken;

import java.math.BigInteger;

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
        var p = JsonCommonModule.JSON_FACTORY.createParser(json);
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
}
