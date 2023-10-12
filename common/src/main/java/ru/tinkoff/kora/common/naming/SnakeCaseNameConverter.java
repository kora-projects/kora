package ru.tinkoff.kora.common.naming;

import jakarta.annotation.Nonnull;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Пример / Example:
 * <br>
 * <pre>
 * "myFieldNAME" &#8594; "my_field_name"
 * </pre>
 */
public final class SnakeCaseNameConverter implements NameConverter {
    public static final SnakeCaseNameConverter INSTANCE = new SnakeCaseNameConverter();

    @Nonnull
    @Override
    public String convert(@Nonnull String originalName) {
        final String[] splitted = originalName.split("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|( +)");
        return Stream.of(splitted)
            .map(String::toLowerCase)
            .collect(Collectors.joining("_"));
    }
}
