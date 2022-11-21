package ru.tinkoff.kora.kafka.annotation.processor;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.kafka.common.annotation.KafkaIncoming;

import javax.annotation.Nullable;

@Component
public final class KafkaConsumersComponent {

    @KafkaIncoming("kafka.first")
    public void processRecordWithConsumer(ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
        System.out.println(record);
        consumer.commitAsync();
    }

    @KafkaIncoming("kafka.second")
    public void processRecords(ConsumerRecords<String, String> records) {
        final String[] messages = {""};
        records.forEach(record -> messages[0] += record.key() + " " + record.value());
        System.out.println(messages);
    }

    @KafkaIncoming("kafka.third")
    public void processRecord(ConsumerRecord<String, String> record) {
        System.out.println(record);
    }

    @KafkaIncoming("kafka.fourth")
    public void processValue(String event) {
        System.out.println(event);
    }

    @KafkaIncoming("kafka.fifth")
    public void processRecordsWithConsumer(ConsumerRecords<String, String> record, Consumer<String, String> consumer) {
        System.out.println(record);
        consumer.commitAsync();
    }

    @KafkaIncoming("kafka.value.exception")
    public void processValueWithException(@Nullable String value, @Nullable Exception e) {
        System.out.println(value + " - " + e);
    }

    @KafkaIncoming("kafka.key-value.exception")
    public void processKeyValueWithException(@Nullable String key, @Nullable String value, @Nullable Exception e) {
        System.out.println(key + " - " + value + " - " + e);
    }

    public void mustNotBeProcessed(int value) {}

}
