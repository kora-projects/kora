package ru.tinkoff.kora.json.common;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;

public class JsonObjectCodecTest {

    @Test
    void readNestedMap() throws IOException {
        //language=json
        var json = """
            {
              "template": {
                "data": {
                  "url": "https://tinkoff.ru",
                  "number": 1
                }
              }
            }
            """;

        try (var parser = JsonCommonModule.JSON_FACTORY.createParser(json)) {
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
                .containsEntry("url", "https://tinkoff.ru")
                .containsEntry("number", BigInteger.ONE);
        }
    }
}
