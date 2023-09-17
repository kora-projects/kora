package ru.tinkoff.kora.kafka.symbol.processor.producer

import org.apache.kafka.common.serialization.Serializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.kafka.common.producer.KafkaPublisherConfig
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerTelemetryFactory
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import java.util.*

class KafkaPublisherTest : AbstractSymbolProcessorTest() {
    override fun commonImports(): String {
        return super.commonImports() + """
            import ru.tinkoff.kora.kafka.common.producer.TransactionalPublisher
            import ru.tinkoff.kora.kafka.common.annotation.KafkaPublisher
            import ru.tinkoff.kora.kafka.common.annotation.KafkaPublisher.Topic
            import org.apache.kafka.clients.producer.ProducerRecord
            import org.apache.kafka.common.header.Headers
            import org.apache.kafka.common.header.Header
            import org.apache.kafka.clients.producer.Callback
            import org.apache.kafka.clients.producer.RecordMetadata
        """.trimIndent()
    }

    @Test
    fun testPublisherWithRecord() {
        compile(
            listOf(KafkaPublisherSymbolProcessorProvider()), """
            @KafkaPublisher("test")
            interface TestProducer {
              fun send(record: ProducerRecord<String, String>)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = loadClass("\$TestProducer_Impl")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(KafkaProducerTelemetryFactory::class.java, Properties::class.java, Serializer::class.java)
    }

    @Test
    fun testPublisherWithRecordAndCallback() {
        compile(
            listOf(KafkaPublisherSymbolProcessorProvider()), """
            @KafkaPublisher("test")
            interface TestProducer {
              fun send(record: ProducerRecord<String, String>, callback: Callback)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = loadClass("\$TestProducer_Impl")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(KafkaProducerTelemetryFactory::class.java, Properties::class.java, Serializer::class.java)
    }

    @Test
    fun testPublisherWithRecordWithKeyTag() {
        compile(
            listOf(KafkaPublisherSymbolProcessorProvider()), """
            @KafkaPublisher("test")
            interface TestProducer {
              fun send(record: ProducerRecord<@Tag(String::class) String, String>, callback: Callback)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = loadClass("\$TestProducer_PublisherModule")
        assertThat(clazz).isNotNull()
        val m = clazz.getMethod("testProducer_PublisherFactory", KafkaProducerTelemetryFactory::class.java, KafkaPublisherConfig::class.java, Serializer::class.java, Serializer::class.java)
        assertThat(m).isNotNull()
        assertThat(m.parameters[2].getAnnotationsByType(Tag::class.java)).isNotEmpty()
        assertThat(m.parameters[2].getAnnotationsByType(Tag::class.java)[0].value).isEqualTo(arrayOf(String::class))
        assertThat(m.parameters[3].getAnnotationsByType(Tag::class.java)).isEmpty()
    }

    @Test
    fun testPublisherWithRecordWithValueTag() {
        compile(
            listOf(KafkaPublisherSymbolProcessorProvider()), """
            @KafkaPublisher("test")
            interface TestProducer {
              fun send(record: ProducerRecord<String, @Tag(String::class) String>)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = loadClass("\$TestProducer_PublisherModule")
        assertThat(clazz).isNotNull()
        val m = clazz.getMethod("testProducer_PublisherFactory", KafkaProducerTelemetryFactory::class.java, KafkaPublisherConfig::class.java, Serializer::class.java, Serializer::class.java)
        assertThat(m).isNotNull()
        assertThat(m.parameters[2].getAnnotationsByType(Tag::class.java)).isEmpty()
        assertThat(m.parameters[3].getAnnotationsByType(Tag::class.java)).isNotEmpty()
        assertThat(m.parameters[3].getAnnotationsByType(Tag::class.java)[0].value).isEqualTo(arrayOf(String::class))
    }

    @Test
    fun testPublisherWithValue() {
        compile(
            listOf(KafkaPublisherSymbolProcessorProvider()), """
            @ru.tinkoff.kora.kafka.common.annotation.KafkaPublisher("test")
            interface TestProducer {
              @Topic("test.sendTopic")
              fun send(value: String)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$TestProducer_Impl")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(KafkaProducerTelemetryFactory::class.java, Properties::class.java, compileResult.loadClass("\$TestProducer_TopicConfig"), Serializer::class.java)
    }

    @Test
    fun testPublisherWithValueAndCallback() {
        compile(
            listOf(KafkaPublisherSymbolProcessorProvider()), """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic("test")
              fun send(value: String, callback: Callback)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$TestProducer_Impl")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(KafkaProducerTelemetryFactory::class.java, Properties::class.java, compileResult.loadClass("\$TestProducer_TopicConfig"), Serializer::class.java)
    }

    @Test
    fun testPublisherWithValueAndHeaders() {
        compile(
            listOf(KafkaPublisherSymbolProcessorProvider()), """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic("test.sendTopic")
              fun send(value: String, headers: Headers)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$TestProducer_Impl")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(KafkaProducerTelemetryFactory::class.java, Properties::class.java, compileResult.loadClass("\$TestProducer_TopicConfig"), Serializer::class.java)
    }

    @Test
    fun testPublisherWithValueWithTag() {
        compile(
            listOf(KafkaPublisherSymbolProcessorProvider()), """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic("test.sendTopic")
              fun send(@Tag(String::class) value: String)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$TestProducer_PublisherModule")
        val m = clazz.getMethod("testProducer_PublisherFactory", KafkaProducerTelemetryFactory::class.java, KafkaPublisherConfig::class.java, compileResult.loadClass("\$TestProducer_TopicConfig"), Serializer::class.java)
        assertThat(m).isNotNull()
        assertThat(m.parameters[3].getAnnotationsByType(Tag::class.java)).isNotEmpty()
        assertThat(m.parameters[3].getAnnotationsByType(Tag::class.java)[0].value).isEqualTo(arrayOf(String::class))
    }

    @Test
    fun testPublisherWithKeyAndValue() {
        compile(
            listOf(KafkaPublisherSymbolProcessorProvider()), """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic("test.sendTopic")
              fun send(key: Long, value: String)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$TestProducer_Impl")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(KafkaProducerTelemetryFactory::class.java, Properties::class.java, compileResult.loadClass("\$TestProducer_TopicConfig"), Serializer::class.java, Serializer::class.java)
    }

    @Test
    fun testPublisherWithKeyAndValueAndHeaders() {
        compile(
            listOf(KafkaPublisherSymbolProcessorProvider()), """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic("test.sendTopic")
              fun send(key: Long, value: String, headers: Headers)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$TestProducer_Impl")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(KafkaProducerTelemetryFactory::class.java, Properties::class.java, compileResult.loadClass("\$TestProducer_TopicConfig"), Serializer::class.java, Serializer::class.java)
    }

    @Test
    fun testPublisherWithKeyAndValueWithTag() {
        compile(
            listOf(KafkaPublisherSymbolProcessorProvider()), """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic("test.sendTopic")
              fun send(key: Long, @Tag(String::class) value: String)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$TestProducer_PublisherModule")
        val m = clazz.getMethod("testProducer_PublisherFactory", KafkaProducerTelemetryFactory::class.java, KafkaPublisherConfig::class.java, compileResult.loadClass("\$TestProducer_TopicConfig"), Serializer::class.java, Serializer::class.java)
        assertThat(m).isNotNull()
        assertThat(m.parameters[3].getAnnotationsByType(Tag::class.java)).isEmpty()
        assertThat(m.parameters[4].getAnnotationsByType(Tag::class.java)).isNotEmpty()
        assertThat(m.parameters[4].getAnnotationsByType(Tag::class.java)[0].value).isEqualTo(arrayOf(String::class))
    }

    @Test
    fun testPublisherWithValueRelativeConfigPath() {
        compile(
            listOf(KafkaPublisherSymbolProcessorProvider()), """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic(".sendTopic")
              fun send(value: String)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$TestProducer_Impl")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(KafkaProducerTelemetryFactory::class.java, Properties::class.java, compileResult.loadClass("\$TestProducer_TopicConfig"), Serializer::class.java)
    }

    @Test
    fun testTxPublisher() {
        compile(
            listOf(KafkaPublisherSymbolProcessorProvider()), """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic("test.sendTopic")
              fun send(key: Long, value: String)
            }
            """.trimIndent(), """
            @KafkaPublisher("test")
            interface TxProducer : TransactionalPublisher<TestProducer>                
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$TxProducer_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun testReturnVoid() {
        compile(
            listOf(KafkaPublisherSymbolProcessorProvider()), """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic("test.sendTopic")
              fun send(value: String)
            }
            """.trimIndent())
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$TestProducer_Impl")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(KafkaProducerTelemetryFactory::class.java, Properties::class.java, compileResult.loadClass("\$TestProducer_TopicConfig"), Serializer::class.java)
    }

    @Test
    fun testReturnVoidSuspend() {
        compile(
            listOf(KafkaPublisherSymbolProcessorProvider()), """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic("test.sendTopic")
              suspend fun send(value: String)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$TestProducer_Impl")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(KafkaProducerTelemetryFactory::class.java, Properties::class.java, compileResult.loadClass("\$TestProducer_TopicConfig"), Serializer::class.java)
    }

    @Test
    fun testReturnRecordMetadata() {
        compile(
            listOf(KafkaPublisherSymbolProcessorProvider()), """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic("test.sendTopic")
              fun send(value: String): RecordMetadata
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$TestProducer_Impl")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(KafkaProducerTelemetryFactory::class.java, Properties::class.java, compileResult.loadClass("\$TestProducer_TopicConfig"), Serializer::class.java)
    }

    @Test
    fun testReturnRecordMetadataSuspend() {
        compile(
            listOf(KafkaPublisherSymbolProcessorProvider()), """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic("test.sendTopic")
              suspend fun send(value: String): RecordMetadata
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$TestProducer_Impl")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(KafkaProducerTelemetryFactory::class.java, Properties::class.java, compileResult.loadClass("\$TestProducer_TopicConfig"), Serializer::class.java)
    }
}
