package io.koraframework.json.common;

import io.koraframework.json.common.util.JsonObjectCodec;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonEncoding;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;

public class JsonObjectCodecTest {

    @Test
    void readNestedMap() throws IOException {
        //language=json
        var json = """
            {
              "template": {
                "data": {
                  "url": "https://koraframework.io",
                  "number": 1
                }
              }
            }
            """;

        try (var parser = JsonModule.JSON_FACTORY.createParser(json)) {
            parser.nextToken();
            var parseResult = JsonObjectCodec.parse(parser);

            Assertions.assertThat(parseResult)
                .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                .hasSize(1)
                .containsKey("template")
                .extractingByKey("template")
                .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                .hasSize(1)
                .containsKey("data")
                .extractingByKey("data")
                .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                .hasSize(2)
                .containsEntry("url", "https://koraframework.io")
                .containsEntry("number", BigInteger.ONE);
        }
    }

    @Test
    void writeMapWithDateTimeShortAndByte() {
        var map = new LinkedHashMap<String, Object>();
        map.put("dateTime", LocalDateTime.of(2024, 1, 2, 3, 4, 5));
        map.put("date", LocalDate.of(2024, 1, 2));
        map.put("short", (short) 1);
        map.put("byte", (byte) 2);

        var baos = new ByteArrayOutputStream();
        try (var gen = JsonModule.JSON_FACTORY.createGenerator(baos, JsonEncoding.UTF8)) {
            JsonObjectCodec.write(gen, map);
        }

        Assertions.assertThat(baos.toString(StandardCharsets.UTF_8))
            .isEqualTo("{\"dateTime\":\"2024-01-02T03:04:05\",\"date\":\"2024-01-02\",\"short\":1,\"byte\":2}");
    }
}
