package io.koraframework.config.common;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public sealed interface PathElement {

    static PathElement.Index get(int index) {
        return new Index(index);
    }

    static PathElement.Key get(String key) {
        return new Key(key);
    }

    record Index(int index) implements PathElement {
        @Override
        public String toString() {
            return Integer.toString(index);
        }
    }

    final class Key implements PathElement {
        private final String name;
        private volatile List<String> relaxedNames = null;

        private Key(String name) {
            this.name = name;
        }

        public List<String> relaxedNames() {
            var names = this.relaxedNames;
            if (names != null) {
                return names;
            }
            var parsed = parseRelaxedNames(name);
            this.relaxedNames = parsed;
            return parsed;
        }

        public String name() {
            return this.name;
        }

        @Override
        public String toString() {
            return this.name;
        }

        private static List<String> parseRelaxedNames(String fieldName) {
            int len = fieldName.length();
            if (len == 0) {
                return List.of("");
            }

            var kebab = new StringBuilder(len + 4);
            var snake = new StringBuilder(len + 4);

            for (int i = 0; i < len; i++) {
                boolean hasNext = i + 1 < len;
                boolean hasPrevious = i > 0;
                char c = fieldName.charAt(i);

                boolean isDigit = c >= '0' && c <= '9';
                boolean isUppercase = c >= 'A' && c <= 'Z';
                boolean isLowercase = c >= 'a' && c <= 'z';
                boolean isAlphabetic = isUppercase || isLowercase;

                char lowerC = isUppercase ? (char) (c + 32) : c;

                if (hasPrevious) {
                    char prev = fieldName.charAt(i - 1);
                    boolean prevIsDigit = prev >= '0' && prev <= '9';
                    boolean prevIsUppercase = prev >= 'A' && prev <= 'Z';
                    boolean prevIsLowercase = prev >= 'a' && prev <= 'z';
                    boolean prevIsAlphabetic = prevIsUppercase || prevIsLowercase;

                    boolean needDelimiter = false;

                    if (isAlphabetic && isUppercase) {
                        if (!prevIsAlphabetic) {
                            needDelimiter = true;
                        } else if (prevIsLowercase) {
                            needDelimiter = true;
                        } else if (prevIsUppercase && hasNext) {
                            char next = fieldName.charAt(i + 1);
                            if (next >= 'a' && next <= 'z') {
                                needDelimiter = true;
                            }
                        }
                    } else if (isAlphabetic && isLowercase) {
                        if (!prevIsAlphabetic) {
                            needDelimiter = true;
                        }
                    } else if (isDigit) {
                        if (!prevIsDigit) {
                            needDelimiter = true;
                        }
                    } else if (!isAlphabetic) {
                        if (prevIsAlphabetic || prevIsDigit) {
                            needDelimiter = true;
                        }
                    }

                    if (needDelimiter) {
                        kebab.append('-');
                        snake.append('_');
                    }
                }

                if (isAlphabetic || isDigit) {
                    kebab.append(lowerC);
                    snake.append(lowerC);
                } else {
                    kebab.append(c);
                    snake.append(c);
                }
            }

            String kebabStr = kebab.toString();
            String snakeStr = snake.toString();

            if (fieldName.equals(kebabStr) && fieldName.equals(snakeStr)) {
                return List.of(fieldName);
            }

            var result = new ArrayList<String>(3);
            result.add(fieldName);
            if (!kebabStr.equals(fieldName)) {
                result.add(kebabStr);
            }
            if (!snakeStr.equals(fieldName) && !snakeStr.equals(kebabStr)) {
                result.add(snakeStr);
            }
            return result;
        }
    }
}
