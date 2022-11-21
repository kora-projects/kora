package ru.tinkoff.kora.common.naming;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SnakeCaseNameConverter implements NameConverter{
    @Override
    public String convert(String originalName) {
        return Stream.of(originalName.split("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])"))
            .map(String::toLowerCase)
            .collect(Collectors.joining("_"));
    }
}
