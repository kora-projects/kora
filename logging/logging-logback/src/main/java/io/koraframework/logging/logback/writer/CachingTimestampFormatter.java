package io.koraframework.logging.logback.writer;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Formats record timestamps in UTC, reusing the last formatted value while the millisecond does not change, which is
 * the common case for bursts of records.
 * <p>
 * The timestamp and its formatted value are published together as one immutable pair, so concurrent callers never see
 * the value of one millisecond paired with another; at worst two of them format the same millisecond twice.
 */
public final class CachingTimestampFormatter {

    private final DateTimeFormatter formatter;

    private volatile Cached cached = new Cached(Long.MIN_VALUE, "");

    public CachingTimestampFormatter(DateTimeFormatter formatter) {
        this.formatter = formatter;
    }

    public String format(long timestamp) {
        var current = this.cached;
        if (current.timestamp() == timestamp) {
            return current.formatted();
        }
        var formatted = this.formatter.format(Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC));
        this.cached = new Cached(timestamp, formatted);
        return formatted;
    }

    private record Cached(long timestamp, String formatted) { }
}
