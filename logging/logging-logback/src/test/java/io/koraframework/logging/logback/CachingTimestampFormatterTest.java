package io.koraframework.logging.logback;

import org.junit.jupiter.api.Test;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class CachingTimestampFormatterTest {

    @Test
    void shouldFormatInUtc() {
        var formatter = new CachingTimestampFormatter(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        assertThat(formatter.format(1000)).isEqualTo("1970-01-01T00:00:01Z");
    }

    @Test
    void shouldReuseValueForSameMillisecond() {
        var formatter = new CachingTimestampFormatter(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        var first = formatter.format(1000);

        assertThat(formatter.format(1000)).isSameAs(first);
        assertThat(formatter.format(2000)).isEqualTo("1970-01-01T00:00:02Z");
    }

    @Test
    void shouldNeverPairTimestampWithAnotherMillisecondValue() throws Exception {
        var formatter = new CachingTimestampFormatter(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        var mismatch = new AtomicBoolean();

        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int thread = 0; thread < 8; thread++) {
                executor.submit(() -> {
                    for (long i = 0; i < 20_000; i++) {
                        var timestamp = i % 7;
                        var expected = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                            .format(java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneOffset.UTC));
                        if (!formatter.format(timestamp).equals(expected)) {
                            mismatch.set(true);
                        }
                    }
                });
            }
        }

        assertThat(mismatch).isFalse();
    }
}
