package io.koraframework.database.jdbc.postgres;

import org.jspecify.annotations.Nullable;

/**
 * PostgreSQL range value: [lower, upper] with per-bound inclusivity.
 * A {@code null} bound means unbounded (infinite) on that side.
 */
public record Range<T>(@Nullable T lower,
                       @Nullable T upper,
                       boolean lowerInclusive,
                       boolean upperInclusive) {

    public static <T> Range<T> closedOpen(@Nullable T lower, @Nullable T upper) {
        return new Range<>(lower, upper, true, false);
    }

    public static <T> Range<T> closed(@Nullable T lower, @Nullable T upper) {
        return new Range<>(lower, upper, true, true);
    }

    public static <T> Range<T> openClosed(@Nullable T lower, @Nullable T upper) {
        return new Range<>(lower, upper, false, true);
    }

    public static <T> Range<T> open(@Nullable T lower, @Nullable T upper) {
        return new Range<>(lower, upper, false, false);
    }
}
