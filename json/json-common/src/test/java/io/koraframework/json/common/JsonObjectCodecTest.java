package io.koraframework.json.common;

import io.koraframework.json.common.util.JsonObjectCodec;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

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
    void bigDecimalWrittenAsPlainReadsBack() throws IOException {
        var module = new JsonModule() {};
        var writer = module.bigDecimalJsonWriter();
        var reader = module.bigDecimalJsonReader();
        for (var value : new String[]{"1E+1200", "-1E+9999", "-1E-9999", "-123456789.123456789"}) {
            var bigDecimal = new BigDecimal(value);

            var json = writer.toByteArray(bigDecimal);

            Assertions.assertThat(reader.read(json)).isEqualByComparingTo(bigDecimal);
        }
    }
}
