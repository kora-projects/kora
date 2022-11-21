package ru.tinkoff.kora.kafka.common.exceptions;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.errors.SerializationException;

public final class RecordKeyDeserializationException extends SerializationException {
    private final ConsumerRecord<byte[], byte[]> record;

    public RecordKeyDeserializationException(String message, Throwable cause, ConsumerRecord<byte[], byte[]> record) {
        super(message, cause);
        this.record = record;
    }

    public RecordKeyDeserializationException(String message, ConsumerRecord<byte[], byte[]> record) {
        super(message);
        this.record = record;
    }

    public RecordKeyDeserializationException(Throwable cause, ConsumerRecord<byte[], byte[]> record) {
        super(cause);
        this.record = record;
    }

    public RecordKeyDeserializationException(ConsumerRecord<byte[], byte[]> record) {
        super();
        this.record = record;
    }


    public ConsumerRecord<byte[], byte[]> getRecord() {
        return record;
    }
}
