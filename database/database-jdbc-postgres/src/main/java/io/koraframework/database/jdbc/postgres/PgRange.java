package io.koraframework.database.jdbc.postgres;

import org.jspecify.annotations.Nullable;

/**
 * <b>Русский</b>: Значение range-типа PostgreSQL: нижняя и верхняя границы с признаком включённости каждой из них.
 * Граница {@code null} означает бесконечность с этой стороны.
 * <hr>
 * <b>English</b>: PostgreSQL range type value: lower and upper bounds with per-bound inclusivity.
 * A {@code null} bound means unbounded (infinite) on that side.
 * <br>
 * <br>
 * Обратите внимание / Note: PostgreSQL приводит дискретные range-типы ({@code int4range}, {@code int8range},
 * {@code daterange}) к канонической форме {@code [)}, а вырожденные диапазоны — к {@link #empty()},
 * поэтому прочитанное значение может отличаться от записанного. / PostgreSQL canonicalizes discrete range types
 * to the {@code [)} form and degenerate ranges to {@link #empty()}, so a value read back may differ from the
 * one written.
 *
 * @param lower          нижняя граница диапазона / range lower bound
 * @param upper          верхняя граница диапазона / range upper bound
 * @param lowerInclusive включена ли нижняя граница / whether the lower bound is included
 * @param upperInclusive включена ли верхняя граница / whether the upper bound is included
 * @param isEmpty        является ли диапазон пустым / whether the range is empty
 */
public record PgRange<T>(@Nullable T lower,
                         @Nullable T upper,
                         boolean lowerInclusive,
                         boolean upperInclusive,
                         boolean isEmpty) {

    public PgRange {
        if (isEmpty) {
            lower = null;
            upper = null;
            lowerInclusive = false;
            upperInclusive = false;
        }
    }

    public PgRange(@Nullable T lower, @Nullable T upper, boolean lowerInclusive, boolean upperInclusive) {
        this(lower, upper, lowerInclusive, upperInclusive, false);
    }

    /**
     * <b>Русский</b>: Пустой диапазон, не содержащий ни одного значения ({@code empty} в PostgreSQL).
     * <hr>
     * <b>English</b>: Empty range that contains no values ({@code empty} in PostgreSQL).
     */
    public static <T> PgRange<T> empty() {
        return new PgRange<>(null, null, false, false, true);
    }

    public static <T> PgRange<T> closed(@Nullable T lower, @Nullable T upper) {
        return new PgRange<>(lower, upper, true, true);
    }

    public static <T> PgRange<T> closedOpen(@Nullable T lower, @Nullable T upper) {
        return new PgRange<>(lower, upper, true, false);
    }

    public static <T> PgRange<T> openClosed(@Nullable T lower, @Nullable T upper) {
        return new PgRange<>(lower, upper, false, true);
    }

    public static <T> PgRange<T> open(@Nullable T lower, @Nullable T upper) {
        return new PgRange<>(lower, upper, false, false);
    }
}
