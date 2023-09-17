package ru.tinkoff.kora.kafka.common.exceptions;

import org.apache.kafka.common.KafkaException;

public final class KafkaPublishException extends KafkaException {

    public KafkaPublishException(Throwable cause) {
        super(cause);
    }
}
