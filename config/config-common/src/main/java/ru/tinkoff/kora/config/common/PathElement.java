package ru.tinkoff.kora.config.common;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
        private final List<String> relaxedNames;

        public Key(String name) {
            this.name = name;
            this.relaxedNames = new ArrayList<>(3);
        }

        public List<String> relaxedNames() {
            if (!this.relaxedNames.isEmpty()) {
                return this.relaxedNames;
            }
            this.relaxedNames.addAll(parseRelaxedNames(name));// todo maybe some way to provide custom parser?
            return this.relaxedNames;
        }

        public String name() {
            return this.name;
        }

        @Override
        public String toString() {
            return this.name;
        }

        private static Set<String> parseRelaxedNames(String fieldName) {
            var parts = new ArrayList<String>();
            int prevI = 0;
            for (int i = 0; i < fieldName.length(); i++) {
                var hasNext = i + 1 < fieldName.length();
                var hasPrevious = i > 0;
                var c = fieldName.charAt(i);
                var isDigit = Character.isDigit(c);
                var isAlphabetic = Character.isAlphabetic(c);
                var isUppercase = Character.isUpperCase(c);
                var isLowercase = Character.isLowerCase(c);
                if (hasPrevious) {
                    var prev = fieldName.charAt(i - 1);
                    if (isAlphabetic && isUppercase) {
                        if (!Character.isAlphabetic(prev)) {
                            parts.add(fieldName.substring(prevI, i).toLowerCase());
                            prevI = i;
                        } else if (Character.isLowerCase(prev)) {
                            parts.add(fieldName.substring(prevI, i).toLowerCase());
                            prevI = i;
                        } else if (Character.isUpperCase(prev) && hasNext && Character.isLowerCase(fieldName.charAt(i + 1))) {
                            parts.add(fieldName.substring(prevI, i).toLowerCase());
                            prevI = i;
                        }
                    } else if (isAlphabetic && isLowercase) {
                        if (!Character.isAlphabetic(prev)) {
                            parts.add(fieldName.substring(prevI, i).toLowerCase());
                            prevI = i;
                        }
                    } else if (isDigit) {
                        if (!Character.isDigit(prev)) {
                            parts.add(fieldName.substring(prevI, i).toLowerCase());
                            prevI = i;
                        }
                    } else if (!isAlphabetic) {
                        if (Character.isLetterOrDigit(prev)) {
                            parts.add(fieldName.substring(prevI, i).toLowerCase());
                            prevI = i;
                        }
                    }
                }
                if (!hasNext) {
                    parts.add(fieldName.substring(prevI, i + 1).toLowerCase());
                }
            }

            var kebab = String.join("-", parts);
            var snake = String.join("_", parts);

            return new LinkedHashSet<>(List.of(fieldName, kebab, snake));
        }
    }

}
