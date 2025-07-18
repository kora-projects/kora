package ru.tinkoff.kora.kafka.common.exceptions;

/**
 * Exception that will be reported to telemetry and skipped
 * <p>
 * Example:
 * <pre>
 * {@code
 * class MyException extends RuntimeException implements SkippableRecordException {
 *    public MyException(String message) {
 *       super(message);}
 * }
 * }
 * </pre>
 */
public interface SkippableRecordException {}
