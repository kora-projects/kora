package ru.tinkoff.kora.common.naming;

/**
 * Пример / Example:
 * <br>
 * <pre>
 * "myFieldNAME" &#8594; "myFieldNAME"
 * </pre>
 */
public final class NoopNameConverter implements NameConverter {

    @Override
    public String convert(String originalName) {
        return originalName;
    }
}
