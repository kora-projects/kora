package ru.tinkoff.kora.validation.common;

import javax.annotation.Nonnull;

record SimpleValidationContext(Path path, boolean isFailFast) implements ValidationContext {

    record SimpleFieldPath(Path root, String value) implements ValidationContext.Path {

        static final Path ROOT = new SimpleFieldPath(null, "");

        @Override
        public String toString() {
            if (root == null) {
                return value;
            }

            final String rootFull = root.full();
            if (rootFull.isBlank()) {
                return value;
            }

            return rootFull + "." + value;
        }
    }

    record SimpleIndexPath(Path root, int index) implements ValidationContext.Path {

        @Override
        public String value() {
            return "[" + index + "]";
        }

        @Override
        public String toString() {
            return (root == null)
                ? value()
                : root + "." + value();
        }
    }

    record SimpleBuilder(Path path, boolean failFast) implements ValidationContext.Builder {

        @Nonnull
        @Override
        public Builder failFast(boolean isFailFast) {
            return new SimpleBuilder(path, isFailFast);
        }

        @Nonnull
        @Override
        public ValidationContext build() {
            return new SimpleValidationContext(SimpleFieldPath.ROOT, failFast);
        }
    }
}
