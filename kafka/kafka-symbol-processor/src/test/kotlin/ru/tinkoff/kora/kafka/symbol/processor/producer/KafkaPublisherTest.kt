package ru.tinkoff.kora.kafka.symbol.processor.producer

import org.apache.kafka.common.serialization.Serializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.kafka.common.producer.PublisherConfig
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerTelemetryFactory
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest

class KafkaPublisherTest : AbstractSymbolProcessorTest() {
    @Test
    fun testProducer() {
        compile(listOf(KafkaPublisherSymbolProcessorProvider()), """
            @ru.tinkoff.kora.kafka.common.annotation.KafkaPublisher("test")
            interface TestProducer : org.apache.kafka.clients.producer.Producer<ByteArray, ByteArray>
            
            """.trimIndent())
        compileResult.assertSuccess()
        val clazz = loadClass("\$TestProducer_Implementation")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(KafkaProducerTelemetryFactory::class.java, PublisherConfig::class.java, Serializer::class.java, Serializer::class.java)
    }

    @Test
    fun testProducerWithKeyTag() {
        compile(listOf(KafkaPublisherSymbolProcessorProvider()), """
            @ru.tinkoff.kora.kafka.common.annotation.KafkaPublisher("test")
            interface TestProducer : org.apache.kafka.clients.producer.Producer<@Tag(String::class)  ByteArray, ByteArray>
            
            """.trimIndent())
        compileResult.assertSuccess()
        val clazz = loadClass("\$TestProducer_Implementation")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(KafkaProducerTelemetryFactory::class.java, PublisherConfig::class.java, Serializer::class.java, Serializer::class.java)
    }

    @Test
    fun testTransactionalProducer() {
        compile(listOf(KafkaPublisherSymbolProcessorProvider()), """
            @ru.tinkoff.kora.kafka.common.annotation.KafkaPublisher("test")
            interface TestProducer : ru.tinkoff.kora.kafka.common.producer.TransactionalProducer<ByteArray, ByteArray>
            
            """.trimIndent())
        compileResult.assertSuccess()
        val clazz = loadClass("\$TestProducer_Implementation")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(KafkaProducerTelemetryFactory::class.java, PublisherConfig::class.java, Serializer::class.java, Serializer::class.java)
    }
}
