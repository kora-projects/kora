package ru.tinkoff.kora.kafka.common.exceptions;

import jakarta.annotation.Nonnull;

public class KafkaSkipRecordException extends RuntimeException {

    /**
     * @param cause will be reported to telemetry and skipped
     * <p>
     * Example:
     * <pre>
     * {@code
     * throw new KafkaSkipRecordException(new MyException());
     * </pre>
     */
    public KafkaSkipRecordException(@Nonnull Throwable cause) {
        super(cause);
    }
}
