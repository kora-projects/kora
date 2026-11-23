package io.koraframework.kafka.common.producer.serializer;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class JsonKafkaSerializerTest {

    private final JsonKafkaSerializer<String> serializer = new JsonKafkaSerializer<>((gen, value) -> {
        if (value == null) {
            gen.writeNull();
        } else {
            gen.writeString(value);
        }
    });

    @Test
    void serializesNullValueAsTombstone() {
        assertThat(serializer.serialize("topic", null)).isNull();
    }

    @Test
    void serializesValueAsJson() {
        assertThat(serializer.serialize("topic", "value")).isEqualTo("\"value\"".getBytes(StandardCharsets.UTF_8));
    }
}
