package ru.tinkoff.kora.common.naming;

import jakarta.annotation.Nonnull;

/**
 * Пример / Example:
 * <br>
 * <pre>
 * "myFieldNAME" &#8594; "myFieldNAME"
 * </pre>
 */
public final class NoopNameConverter implements NameConverter {

    @Nonnull
    @Override
    public String convert(@Nonnull String originalName) {
        return originalName;
    }
}
