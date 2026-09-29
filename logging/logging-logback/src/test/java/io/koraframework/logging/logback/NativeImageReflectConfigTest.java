package io.koraframework.logging.logback;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class NativeImageReflectConfigTest {

    private static final String REFLECT_CONFIG = "/META-INF/native-image/io.koraframework.logging.logback/reflect-config.json";
    private static final Pattern NAME = Pattern.compile("\"name\"\\s*:\\s*\"(io\\.koraframework\\.[^\"]+)\"");

    @Test
    void everyKoraClassInReflectConfigExists() throws IOException {
        String json;
        try (var is = NativeImageReflectConfigTest.class.getResourceAsStream(REFLECT_CONFIG)) {
            assertThat(is).isNotNull();
            json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        var matcher = NAME.matcher(json);
        var count = 0;
        while (matcher.find()) {
            var className = matcher.group(1);
            count++;
            assertThatCode(() -> Class.forName(className, false, NativeImageReflectConfigTest.class.getClassLoader()))
                .as(className)
                .doesNotThrowAnyException();
        }
        assertThat(count).isPositive();
    }
}
