package ru.tinkoff.kora.validation.common;


/**
 * Context of current validation progress and validation options
 */
public interface ValidationContext {

    /**
     * @return path where violation occurred
     */
    Path path();

    /**
     * @return {@link Boolean#TRUE} when should fail on first occurred violation
     */
    boolean isFailFast();

    default ValidationContext addPath(String path) {
        return new SimpleValidationContext(path().add(path), isFailFast());
    }

    default ValidationContext addPath(int pathIndex) {
        return new SimpleValidationContext(path().add(pathIndex), isFailFast());
    }

    /**
     * @param message of violation
     * @return violation for current context
     */
    default Violation violates(String message) {
        SimpleViolation.logger.debug("Validation violation on path '{}' with error: {}", path(), message);
        return new SimpleViolation(message, path());
    }

    static Builder builder() {
        return new SimpleValidationContext.SimpleBuilder(SimpleValidationContext.SimpleFieldPath.ROOT, false);
    }

    static ValidationContext full() {
        return new SimpleValidationContext.SimpleBuilder(SimpleValidationContext.SimpleFieldPath.ROOT, false)
            .build();
    }

    static ValidationContext failFast() {
        return new SimpleValidationContext.SimpleBuilder(SimpleValidationContext.SimpleFieldPath.ROOT, true)
            .build();
    }

    /**
     * Indicates deep object path for violation and validation context
     */
    interface Path {

        /**
         * @return current path value (field name or index in array)
         */
        String value();

        /**
         * @return root path if exist
         */
        Path root();

        default Path add(String field) {
            return new SimpleValidationContext.SimpleFieldPath(this, field);
        }

        default Path add(int index) {
            return new SimpleValidationContext.SimpleIndexPath(this, index);
        }

        static Path of(String path) {
            return new SimpleValidationContext.SimpleFieldPath(null, path);
        }

        /**
         * @return full path concatenated to string
         */
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
            Builder failFast(boolean isFailFast);

            ValidationContext build();
    }
}
