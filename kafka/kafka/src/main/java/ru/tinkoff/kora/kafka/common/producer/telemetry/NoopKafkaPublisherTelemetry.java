package ru.tinkoff.kora.kafka.common.producer.telemetry;

public class NoopKafkaPublisherTelemetry implements KafkaPublisherTelemetry {
    public static final NoopKafkaPublisherTelemetry INSTANCE = new  NoopKafkaPublisherTelemetry();

    @Override
    public KafkaPublisherTransactionObservation observeTx() {
        return NoopKafkaPublisherTransactionObservation.INSTANCE;
    }

    @Override
    public KafkaPublisherRecordObservation observeSend(String topic) {
        return NoopKafkaPublisherRecordObservation.INSTANCE;
    }
}
