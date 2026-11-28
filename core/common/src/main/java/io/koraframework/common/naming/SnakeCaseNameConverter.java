package io.koraframework.common.naming;

/**
 * Пример / Example:
 * <br>
 * <pre>
 * "myFieldNAME" &#8594; "my_field_name"
 * </pre>
 */
public final class SnakeCaseNameConverter implements NameConverter {
    public static final SnakeCaseNameConverter INSTANCE = new SnakeCaseNameConverter();

    @Override
    public String convert(String originalName) {
        if (originalName == null) {
            return null;
        }
        if (originalName.isEmpty()) {
            return "";
        }

        int length = originalName.length();
        StringBuilder sb = new StringBuilder(length + 4);

        for (int i = 0; i < length; i++) {
            char current = originalName.charAt(i);

            if (current == ' ') {
                if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '_') {
                    sb.append('_');
                }
                continue;
            }

            if (i > 0) {
                char prev = originalName.charAt(i - 1);

                if (Character.isLowerCase(prev) && Character.isUpperCase(current)) {
                    if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '_') {
                        sb.append('_');
                    }
                }
                else if (Character.isUpperCase(prev) && Character.isUpperCase(current) && (i + 1 < length)) {
                    char next = originalName.charAt(i + 1);
                    if (Character.isLowerCase(next)) {
                        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '_') {
                            sb.append('_');
                        }
                    }
                }
            }

            sb.append(Character.toLowerCase(current));
        }

        int sbLength = sb.length();
        if (sbLength > 0 && sb.charAt(sbLength - 1) == '_') {
            sb.setLength(sbLength - 1);
        }

        return sb.toString();
    }
}
