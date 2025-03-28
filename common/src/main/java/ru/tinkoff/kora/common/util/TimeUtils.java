package ru.tinkoff.kora.common.util;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public final class TimeUtils {

    private TimeUtils() { }

    public static long started() {
        return System.nanoTime();
    }

    public static Duration took(long started) {
        return Duration.ofNanos(System.nanoTime() - started).truncatedTo(ChronoUnit.MILLIS);
    }

    public static String tookForLogging(long started) {
        return durationForLogging(System.nanoTime() - started);
    }

    public static String durationForLogging(long duration) {
        return Duration.ofNanos(duration).truncatedTo(ChronoUnit.MILLIS).toString().substring(2).toLowerCase();
    }
}
