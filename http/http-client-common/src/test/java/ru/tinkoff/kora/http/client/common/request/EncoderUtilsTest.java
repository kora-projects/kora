package ru.tinkoff.kora.http.client.common.request;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class EncoderUtilsTest {

    @Test
    void disableEncodeSpaceToPlus() {
        var actual = EncoderUtils.encode("A A", StandardCharsets.UTF_8, true);
        var excepted = "A%20A";

        assertThat(actual).isEqualTo(excepted);
    }

    @Test
    void enableEncodeSpaceToPlus() {
        var actual = EncoderUtils.encode("A A", StandardCharsets.UTF_8, false);
        var excepted = "A+A";

        assertThat(actual).isEqualTo(excepted);
    }
}
