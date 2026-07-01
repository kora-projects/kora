package io.koraframework.common.util;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public final class TimeUtils {

    private TimeUtils() { }

    public static long started() {
        return System.nanoTime();
    }

    public static Duration took(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).truncatedTo(ChronoUnit.MILLIS);
    }

    public static String tookForLogging(long startedNanos) {
        return durationForLogging(System.nanoTime() - startedNanos);
    }

    public static String durationForLogging(long durationNanos) {
        return Duration.ofNanos(durationNanos).truncatedTo(ChronoUnit.MILLIS).toString().substring(2).toLowerCase();
    }

    public static String durationForLogging(Duration duration) {
        return duration.truncatedTo(ChronoUnit.MILLIS).toString().substring(2).toLowerCase();
    }
}
