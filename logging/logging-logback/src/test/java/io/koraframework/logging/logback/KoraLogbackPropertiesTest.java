package io.koraframework.logging.logback;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@Isolated
class KoraLogbackPropertiesTest {

    private static final String PROPERTY = "kora.logging.config.test-duration";

    @AfterEach
    void clearProperty() {
        System.clearProperty(PROPERTY);
    }

    @ParameterizedTest
    @CsvSource({
        "1s, 1000",
        "500ms, 500",
        "1500, 1500",
        "1.5s, 1500",
        "2m, 120000",
        "1h, 3600000",
        "1 s, 1000",
        "1seconds, 1000",
        "250millis, 250",
        "PT2S, 2000",
        "PT0.25S, 250",
        "0, 0"
    })
    void shouldParseKoraDurationFormats(String value, long millis) {
        assertThat(KoraLogbackProperties.parseDuration(value)).isEqualTo(Duration.ofMillis(millis));
    }

    @ParameterizedTest
    @ValueSource(strings = {"soon", "5x", "s", "1M", "1.2.3s"})
    void shouldRejectMalformedDurations(String value) {
        assertThat(KoraLogbackProperties.parseDuration(value)).isNull();
    }

    @Test
    void shouldReadDurationFromProperty() {
        System.setProperty(PROPERTY, "3s");

        var duration = KoraLogbackProperties.getDuration(PROPERTY, Duration.ofSeconds(1), message -> { });

        assertThat(duration).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void shouldFallBackToDefaultAndWarnOnNegativeDuration() {
        System.setProperty(PROPERTY, "-1s");
        var warnings = new ArrayList<String>();

        var duration = KoraLogbackProperties.getDuration(PROPERTY, Duration.ofSeconds(1), warnings::add);

        assertThat(duration).isEqualTo(Duration.ofSeconds(1));
        assertThat(warnings).singleElement().satisfies(warning -> assertThat(warning).startsWith(PROPERTY + "=-1s"));
    }

    @Test
    void shouldUseDefaultWhenPropertyIsAbsent() {
        var duration = KoraLogbackProperties.getDuration(PROPERTY, Duration.ofSeconds(1), message -> { });

        assertThat(duration).isEqualTo(Duration.ofSeconds(1));
    }
}
