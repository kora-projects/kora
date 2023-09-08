package ru.tinkoff.kora.common.naming;

/**
 * Example: "myFieldNAME" will convert to "myFieldName"
 */
public final class CamelCaseNameConverter implements NameConverter {

    @Override
    public String convert(String originalName) {
        final String[] splitted = originalName.split("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|( +)");

        final StringBuilder builder = new StringBuilder(splitted[0].toLowerCase());
        for (int i = 1; i < splitted.length; i++) {
            var word = splitted[i].toLowerCase();
            var wordWithUpper = Character.toUpperCase(word.charAt(0)) + word.substring(1);
            builder.append(wordWithUpper);
        }

        return builder.toString();
    }
}
