package ru.tinkoff.kora.common.naming;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Пример / Example:
 * <br>
 * <pre>
 * "myFieldNAME" &#8594; "MY_FIELD_NAME"
 * </pre>
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
