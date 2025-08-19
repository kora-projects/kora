package ru.tinkoff.kora.kafka.symbol.processor.producer

import org.apache.kafka.common.serialization.Serializer
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.kafka.common.producer.KafkaPublisherConfig
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerTelemetryFactory
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import ru.tinkoff.kora.telemetry.common.TelemetryConfig
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
            import java.util.concurrent.Future
        """.trimIndent()
    }

    fun compile0(@Language("kotlin") vararg sources: String) = compile0(listOf(KafkaPublisherSymbolProcessorProvider()), *sources)

    @Test
    fun testPublisherWithRecord() {
        compile0(
            """
            @KafkaPublisher("test")
            interface TestProducer {
              fun send(record: ProducerRecord<String, String>)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = loadClass("\$TestProducer_Impl")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(KafkaProducerTelemetryFactory::class.java, TelemetryConfig::class.java, Properties::class.java, Serializer::class.java)
    }

    @Test
    fun testPublisherWithRecordAndCallback() {
        compile0(
            """
            @KafkaPublisher("test")
            interface TestProducer {
              fun send(record: ProducerRecord<String, String>, callback: Callback)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = loadClass("\$TestProducer_Impl")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(KafkaProducerTelemetryFactory::class.java, TelemetryConfig::class.java, Properties::class.java, Serializer::class.java)
    }

    @Test
    fun testPublisherWithRecordWithKeyTag() {
        compile0(
            """
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
        compile0(
            """
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
        compile0(
            """
            @ru.tinkoff.kora.kafka.common.annotation.KafkaPublisher("test")
            interface TestProducer {
              @Topic("test.sendTopic")
              fun send(value: String)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = loadClass("\$TestProducer_Impl")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(
            KafkaProducerTelemetryFactory::class.java,
            TelemetryConfig::class.java,
            Properties::class.java,
            loadClass("\$TestProducer_TopicConfig"),
            Serializer::class.java
        )
    }

    @Test
    fun testPublisherWithValueAndCallback() {
        compile0(
            """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic("test")
              fun send(value: String, callback: Callback)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = loadClass("\$TestProducer_Impl")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(
            KafkaProducerTelemetryFactory::class.java,
            TelemetryConfig::class.java,
            Properties::class.java,
            loadClass("\$TestProducer_TopicConfig"),
            Serializer::class.java
        )
    }

    @Test
    fun testPublisherWithValueAndHeaders() {
        compile0(
            """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic("test.sendTopic")
              fun send(value: String, headers: Headers)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = loadClass("\$TestProducer_Impl")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(
            KafkaProducerTelemetryFactory::class.java,
            TelemetryConfig::class.java,
            Properties::class.java,
            loadClass("\$TestProducer_TopicConfig"),
            Serializer::class.java
        )
    }

    @Test
    fun testPublisherWithValueWithTag() {
        compile0(
            """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic("test.sendTopic")
              fun send(@Tag(String::class) value: String)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = loadClass("\$TestProducer_PublisherModule")
        val m = clazz.getMethod(
            "testProducer_PublisherFactory",
            KafkaProducerTelemetryFactory::class.java,
            KafkaPublisherConfig::class.java,
            loadClass("\$TestProducer_TopicConfig"),
            Serializer::class.java
        )
        assertThat(m).isNotNull()
        assertThat(m.parameters[3].getAnnotationsByType(Tag::class.java)).isNotEmpty()
        assertThat(m.parameters[3].getAnnotationsByType(Tag::class.java)[0].value).isEqualTo(arrayOf(String::class))
    }

    @Test
    fun testPublisherWithKeyAndValue() {
        compile0(
            """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic("test.sendTopic")
              fun send(key: Long, value: String)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = loadClass("\$TestProducer_Impl")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(
            KafkaProducerTelemetryFactory::class.java,
            TelemetryConfig::class.java,
            Properties::class.java,
            loadClass("\$TestProducer_TopicConfig"),
            Serializer::class.java,
            Serializer::class.java
        )
    }

    @Test
    fun testPublisherWithKeyAndValueAndHeaders() {
        compile0(
            """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic("test.sendTopic")
              fun send(key: Long, value: String, headers: Headers)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = loadClass("\$TestProducer_Impl")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(
            KafkaProducerTelemetryFactory::class.java,
            TelemetryConfig::class.java,
            Properties::class.java,
            loadClass("\$TestProducer_TopicConfig"),
            Serializer::class.java,
            Serializer::class.java
        )
    }

    @Test
    fun testPublisherWithKeyAndValueWithTag() {
        compile0(
            """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic("test.sendTopic")
              fun send(key: Long, @Tag(String::class) value: String)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = loadClass("\$TestProducer_PublisherModule")
        val m = clazz.getMethod(
            "testProducer_PublisherFactory",
            KafkaProducerTelemetryFactory::class.java,
            KafkaPublisherConfig::class.java,
            loadClass("\$TestProducer_TopicConfig"),
            Serializer::class.java,
            Serializer::class.java
        )
        assertThat(m).isNotNull()
        assertThat(m.parameters[3].getAnnotationsByType(Tag::class.java)).isEmpty()
        assertThat(m.parameters[4].getAnnotationsByType(Tag::class.java)).isNotEmpty()
        assertThat(m.parameters[4].getAnnotationsByType(Tag::class.java)[0].value).isEqualTo(arrayOf(String::class))
    }

    @Test
    fun testPublisherWithValueRelativeConfigPath() {
        compile0(
            """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic(".sendTopic")
              fun send(value: String)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = loadClass("\$TestProducer_Impl")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(
            KafkaProducerTelemetryFactory::class.java,
            TelemetryConfig::class.java,
            Properties::class.java,
            loadClass("\$TestProducer_TopicConfig"),
            Serializer::class.java
        )
    }

    @Test
    fun testTxPublisher() {
        compile0(
            """
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
        val clazz = loadClass("\$TxProducer_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun testReturnVoid() {
        compile0(
            """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic("test.sendTopic")
              fun send(value: String)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = loadClass("\$TestProducer_Impl")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(
            KafkaProducerTelemetryFactory::class.java,
            TelemetryConfig::class.java,
            Properties::class.java,
            loadClass("\$TestProducer_TopicConfig"),
            Serializer::class.java
        )
    }

    @Test
    fun testReturnVoidSuspend() {
        compile0(
            """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic("test.sendTopic")
              suspend fun send(value: String)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = loadClass("\$TestProducer_Impl")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(
            KafkaProducerTelemetryFactory::class.java,
            TelemetryConfig::class.java,
            Properties::class.java,
            loadClass("\$TestProducer_TopicConfig"),
            Serializer::class.java
        )
    }

    @Test
    fun testReturnRecordMetadata() {
        compile0(
            """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic("test.sendTopic")
              fun send(value: String): RecordMetadata
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = loadClass("\$TestProducer_Impl")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(
            KafkaProducerTelemetryFactory::class.java,
            TelemetryConfig::class.java,
            Properties::class.java,
            loadClass("\$TestProducer_TopicConfig"),
            Serializer::class.java
        )
    }

    @Test
    fun testReturnRecordMetadataSuspend() {
        compile0(
            """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic("test.sendTopic")
              suspend fun send(value: String): RecordMetadata
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = loadClass("\$TestProducer_Impl")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(
            KafkaProducerTelemetryFactory::class.java,
            TelemetryConfig::class.java,
            Properties::class.java,
            loadClass("\$TestProducer_TopicConfig"),
            Serializer::class.java
        )
    }

    @Test
    fun testReturnFutureRecordMetadata() {
        compile0(
            """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic("test.sendTopic")
              fun send(value: String): Future<RecordMetadata>
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = loadClass("\$TestProducer_Impl")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(
            KafkaProducerTelemetryFactory::class.java,
            TelemetryConfig::class.java,
            Properties::class.java,
            loadClass("\$TestProducer_TopicConfig"),
            Serializer::class.java
        )
    }

    @Test
    fun testAop() {
        compile0(
            """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic("test.sendTopic")
              @ru.tinkoff.kora.logging.common.annotation.Log
              fun send(value: String)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
    }
}
