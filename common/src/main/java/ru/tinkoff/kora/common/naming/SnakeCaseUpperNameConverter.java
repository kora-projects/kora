package ru.tinkoff.kora.common.naming;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Example: "myFieldNAME" will convert to "MY_FIELD_NAME"
 */
public final class SnakeCaseUpperNameConverter implements NameConverter {

    @Override
    public String convert(String originalName) {
        final String[] splitted = originalName.split("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|( +)");
        return Stream.of(splitted)
            .map(String::toUpperCase)
            .collect(Collectors.joining("_"));
    }
}
