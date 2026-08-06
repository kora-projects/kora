package io.koraframework.logging.logback.json.writer;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

final class CachingDateFormatter {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final ZoneOffset UTC = ZoneOffset.UTC;

    private long lastTimestamp = -1;

    private volatile String cachedStr = null;

    String format(long now) {
        final String cachedStr;
        if (now != this.lastTimestamp) {
            this.lastTimestamp = now;
            cachedStr = FORMATTER.format(Instant.ofEpochMilli(now).atZone(UTC));
            this.cachedStr = cachedStr;
        } else {
            cachedStr = this.cachedStr;
        }
        return cachedStr;
    }
}
