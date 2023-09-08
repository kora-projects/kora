package ru.tinkoff.kora.common.naming;

/**
 * Example: "myFieldNAME" will convert to "myFieldNAME"
 */
public final class NoopNameConverter implements NameConverter {

    @Override
    public String convert(String originalName) {
        return originalName;
    }
}
