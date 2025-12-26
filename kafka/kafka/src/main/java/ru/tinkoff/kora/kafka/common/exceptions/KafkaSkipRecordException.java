package ru.tinkoff.kora.kafka.common.exceptions;

import org.jspecify.annotations.NonNull;

/**
 * Wraps exception that will be reported to telemetry and skipped
 * <p>
 * Example:
 * <pre>
 * {@code
 * throw new KafkaSkipRecordException(new MyException());
 * }
 * </pre>
 */

public class KafkaSkipRecordException extends RuntimeException {

    /**
     * @param cause will be reported to telemetry and skipped
     */
    public KafkaSkipRecordException(@NonNull Throwable cause) {
        super(cause);
    }
}
