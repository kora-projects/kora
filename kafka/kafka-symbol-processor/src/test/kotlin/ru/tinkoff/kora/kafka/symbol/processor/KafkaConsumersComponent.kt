package ru.tinkoff.kora.kafka.symbol.processor

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import ru.tinkoff.kora.common.Component
import ru.tinkoff.kora.kafka.common.annotation.KafkaIncoming
import java.lang.Exception

@Component
class KafkaConsumersComponent {
    @KafkaIncoming("kafka.first")
    fun processRecordWithConsumer(
        record: ConsumerRecord<String, String>,
        consumer: Consumer<String, String>
    ) {
        println(record)
        consumer.commitAsync()
    }

    @KafkaIncoming("kafka.second")
    fun processRecords(records: ConsumerRecords<String, String>) {
        val messages = arrayOf("")
        records.forEach { record -> messages[0] += record.key() + " " + record.value() }
        println(messages)
    }

    @KafkaIncoming("kafka.third")
    fun processRecord(record: ConsumerRecord<String, String>) {
        println(record)
    }

    @KafkaIncoming("kafka.fourth")
    fun processValue(event: String) {
        println(event)
    }

    @KafkaIncoming("kafka.fifth")
    fun processRecordsWithConsumer(
        record: ConsumerRecords<String, String>,
        consumer: Consumer<String, String>
    ) {
        println(record)
        consumer.commitAsync()
    }

    @KafkaIncoming("kafka.key.value.incomming")
    fun processKeyValueWithException(
        key: String?,
        value: String?,
        exception: Exception?
    ) {

    }

    @KafkaIncoming("kafka.key.value.incomming")
    fun processValueWithException(
        value: String?,
        exception: Exception?
    ) {

    }

    fun mustNotBeProcessed(value: Int) {}
}
