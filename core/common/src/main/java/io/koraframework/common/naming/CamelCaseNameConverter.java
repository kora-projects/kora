package io.koraframework.common.naming;

/**
 * Пример / Example:
 * <br>
 * <pre>
 * "myFieldNAME" &#8594; "myFieldName"
 * </pre>
 */
public final class CamelCaseNameConverter implements NameConverter {

    @Override
    public String convert(String originalName) {
        if (originalName == null) {
            return null;
        }
        if (originalName.isEmpty()) {
            return "";
        }

        int length = originalName.length();
        StringBuilder sb = new StringBuilder(length);

        boolean isFirstWord = true;
        boolean capitalizeNext = false;

        for (int i = 0; i < length; i++) {
            char current = originalName.charAt(i);

            if (current == ' ') {
                if (!sb.isEmpty()) {
                    isFirstWord = false;
                    capitalizeNext = true;
                }
                continue;
            }

            if (i > 0) {
                char prev = originalName.charAt(i - 1);

                if (Character.isLowerCase(prev) && Character.isUpperCase(current)) {
                    isFirstWord = false;
                    capitalizeNext = true;
                } else if (Character.isUpperCase(prev) && Character.isUpperCase(current) && (i + 1 < length)) {
                    char next = originalName.charAt(i + 1);
                    if (Character.isLowerCase(next)) {
                        isFirstWord = false;
                        capitalizeNext = true;
                    }
                }
            }

            if (isFirstWord) {
                sb.append(Character.toLowerCase(current));
            } else if (capitalizeNext) {
                sb.append(Character.toUpperCase(current));
                capitalizeNext = false;
            } else {
                sb.append(Character.toLowerCase(current));
            }
        }

        return sb.toString();
    }
}
