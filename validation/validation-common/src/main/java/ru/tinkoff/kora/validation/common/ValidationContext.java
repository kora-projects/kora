package ru.tinkoff.kora.validation.common;

import javax.annotation.Nonnull;

import javax.annotation.concurrent.Immutable;

/**
 * Context of current validation progress and validation options
 */
@Immutable
public interface ValidationContext {

    /**
     * @return path where violation occurred
     */
    @Nonnull
    Path path();

    /**
     * @return {@link Boolean#TRUE} when should fail on first occurred violation
     */
    boolean isFailFast();

    @Nonnull
    default ValidationContext addPath(@Nonnull String path) {
        return new SimpleValidationContext(path().add(path), isFailFast());
    }

    @Nonnull
    default ValidationContext addPath(int pathIndex) {
        return new SimpleValidationContext(path().add(pathIndex), isFailFast());
    }

    /**
     * @param message of violation
     * @return violation for current context
     */
    @Nonnull
    default Violation violates(@Nonnull String message) {
        return new SimpleViolation(message, path());
    }

    static Builder builder() {
        return new SimpleValidationContext.SimpleBuilder(SimpleValidationContext.SimpleFieldPath.ROOT, false);
    }

    /**
     * Indicates deep object path for violation and validation context
     */
    @Immutable
    interface Path {

        /**
         * @return current path value (field name or index in array)
         */
        String value();

        /**
         * @return root path if exist
         */
        Path root();

        default Path add(@Nonnull String field) {
            return new SimpleValidationContext.SimpleFieldPath(this, field);
        }

        default Path add(int index) {
            return new SimpleValidationContext.SimpleIndexPath(this, index);
        }

        static Path of(@Nonnull String path) {
            return new SimpleValidationContext.SimpleFieldPath(null, path);
        }

        /**
         * @return full path concatenated to string
         */
        @Nonnull
        default String full() {
            return toString();
        }
    }

    /**
     * Context builder
     */
    interface Builder {

        /**
         * @param isFailFast {@link Boolean#TRUE} when should fail on first occurred violation
         * @return self
         */
        @Nonnull
        Builder failFast(boolean isFailFast);

        @Nonnull
        ValidationContext build();
    }
}
