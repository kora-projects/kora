package io.koraframework.kafka.common.utils;

import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaHeaderUtilsTest {

    @Test
    void masksConfiguredHeadersAndPassesRawValueToStrategy() {
        var headers = new RecordHeaders()
            .add("authorization", "secret".getBytes(StandardCharsets.UTF_8))
            .add("other", "value".getBytes(StandardCharsets.UTF_8));

        var result = KafkaHeaderUtils.toMaskedString(Set.of("authorization"),
            value -> "masked:" + new String((byte[]) value, StandardCharsets.UTF_8), headers);

        assertThat(result).isEqualTo("authorization: masked:secret\nother: value");
    }
}
