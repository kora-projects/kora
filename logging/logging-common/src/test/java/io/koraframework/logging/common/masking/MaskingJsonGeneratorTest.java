package io.koraframework.logging.common.masking;

import io.koraframework.json.common.JsonModule;
import io.koraframework.json.common.JsonWriter;
import io.koraframework.logging.common.arg.MaskedStructuredArgumentMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.core.ObjectReadContext;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingJsonGeneratorTest {
    record Card(String owner, int[] pin, String[] tokens, String body) {}

    static final JsonWriter<Card> WRITER = (gen, c) -> {
        gen.writeStartObject();
        gen.writeName("owner");
        gen.writeString(c.owner());
        gen.writeName("pin");
        gen.writeArray(c.pin(), 0, c.pin().length);
        gen.writeName("tokens");
        gen.writeArray(c.tokens(), 0, c.tokens().length);
        gen.writeName("body");
        try (var p = JsonModule.JSON_FACTORY.createParser(ObjectReadContext.empty(), c.body())) {
            p.nextToken();
            gen.copyCurrentStructure(p);
        }
        gen.writeEndObject();
    };

    @Test
    void masksValuesWrittenWithWriteArrayAndCopiedFromParser() {
        var rules = MaskingRules.builder(Card.class)
            .mask("pin", new MaskingFull())
            .mask("body.password", new MaskingFull())
            .build();
        var card = new Card("bob", new int[]{1, 2, 3, 4}, new String[]{"a", "b"}, "{\"password\":\"secret\",\"login\":\"user\"}");

        var out = new MaskedStructuredArgumentMapper<>(WRITER, rules, true).writeToString(card);

        assertThat(out).isEqualTo("{\"owner\":\"bob\",\"pin\":null,\"tokens\":[\"a\",\"b\"],\"body\":{\"password\":\"***\",\"login\":\"user\"}}");
    }
}
