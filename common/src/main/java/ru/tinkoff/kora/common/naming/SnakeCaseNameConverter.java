package ru.tinkoff.kora.common.naming;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Example: "myFieldNAME" will convert to "my_field_name"
 */
public final class SnakeCaseNameConverter implements NameConverter {
    public static final SnakeCaseNameConverter INSTANCE = new SnakeCaseNameConverter();

    @Override
    public String convert(String originalName) {
        final String[] splitted = originalName.split("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|( +)");
        return Stream.of(splitted)
            .map(String::toLowerCase)
            .collect(Collectors.joining("_"));
    }
}
