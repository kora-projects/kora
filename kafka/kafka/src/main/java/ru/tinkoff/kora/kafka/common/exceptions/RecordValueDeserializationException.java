package ru.tinkoff.kora.kafka.common.exceptions;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.errors.SerializationException;

public final class RecordValueDeserializationException extends SerializationException {
    private final ConsumerRecord<byte[], byte[]> record;

    public RecordValueDeserializationException(String message, Throwable cause, ConsumerRecord<byte[], byte[]> record) {
        super(message, cause);
        this.record = record;
    }

    public RecordValueDeserializationException(String message, ConsumerRecord<byte[], byte[]> record) {
        super(message);
        this.record = record;
    }

    public RecordValueDeserializationException(Throwable cause, ConsumerRecord<byte[], byte[]> record) {
        super(cause);
        this.record = record;
    }

    public RecordValueDeserializationException(ConsumerRecord<byte[], byte[]> record) {
        super();
        this.record = record;
    }


    public ConsumerRecord<byte[], byte[]> getRecord() {
        return record;
    }
}
