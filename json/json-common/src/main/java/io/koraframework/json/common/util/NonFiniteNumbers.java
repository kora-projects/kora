package io.koraframework.json.common.util;

import org.jspecify.annotations.Nullable;

/**
 * Jackson writes non-finite double and float values as the strings "NaN", "Infinity" and "-Infinity"
 * ({@code JsonWriteFeature.WRITE_NAN_AS_STRINGS}), so readers accept these strings back.
 */
public final class NonFiniteNumbers {

    private NonFiniteNumbers() { }

    public static boolean isNonFinite(@Nullable String value) {
        return "NaN".equals(value) || "Infinity".equals(value) || "-Infinity".equals(value);
    }
}
