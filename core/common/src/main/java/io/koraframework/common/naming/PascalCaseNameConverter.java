package io.koraframework.common.naming;

/**
 * Пример / Example:
 * <br>
 * <pre>
 * "myFieldNAME" &#8594; "MyFieldName"
 * </pre>
 */
public final class PascalCaseNameConverter implements NameConverter {

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
        boolean capitalizeNext = true;

        for (int i = 0; i < length; i++) {
            char current = originalName.charAt(i);

            if (current == ' ') {
                capitalizeNext = true;
                continue;
            }

            if (i > 0) {
                char prev = originalName.charAt(i - 1);

                if (Character.isLowerCase(prev) && Character.isUpperCase(current)) {
                    capitalizeNext = true;
                }
                else if (Character.isUpperCase(prev) && Character.isUpperCase(current) && (i + 1 < length)) {
                    char next = originalName.charAt(i + 1);
                    if (Character.isLowerCase(next)) {
                        capitalizeNext = true;
                    }
                }
            }

            if (capitalizeNext) {
                sb.append(Character.toUpperCase(current));
                capitalizeNext = false;
            } else {
                sb.append(Character.toLowerCase(current));
            }
        }

        return sb.toString();
    }
}
