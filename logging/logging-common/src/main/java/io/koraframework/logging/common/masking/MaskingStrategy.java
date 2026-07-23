package io.koraframework.logging.common.masking;

/**
 * Replaces a value matched by {@link MaskingRules} before it is written to a log marker.
 * <p>
 * Implementations may be regular Kora components and can keep configuration in constructor parameters.
 * Built-in implementations include full masking and keeping the first or last characters.
 */
public interface MaskingStrategy {

    /**
     * Returns the value that should be written to the log instead of the original value.
     * <p>
     * For scalar JSON values this method receives the Java value passed to the corresponding
     * {@code JsonGenerator.write*()} method:
     * {@link String}, {@link Boolean}, {@link Number}, {@link java.math.BigInteger},
     * {@link java.math.BigDecimal}, or {@code byte[]} for binary values.
     * JSON null values are not masked and this method is not called for them.
     * For object or array values matched as a whole, this method receives the source value passed to
     * {@code JsonGenerator.writeStartObject(Object)} / {@code writeStartArray(Object)} when the underlying
     * writer provides it. Map keys are not masked as values.
     *
     * @param value original JSON value
     * @return replacement string to write to the log
     */
    String mask(Object value);
}
