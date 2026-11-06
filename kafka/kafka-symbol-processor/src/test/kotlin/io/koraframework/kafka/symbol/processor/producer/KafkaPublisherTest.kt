package io.koraframework.kafka.symbol.processor.producer

import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.TimeoutException
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import io.koraframework.common.annotation.Tag
import io.koraframework.kafka.common.exceptions.KafkaPublishException
import io.koraframework.kafka.common.producer.AbstractPublisher
import io.koraframework.kafka.common.producer.KafkaPublisherConfig
import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherRecordObservation
import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherTelemetry
import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherTelemetryConfig
import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherTelemetryFactory
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import io.opentelemetry.api.trace.Span
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

class KafkaPublisherTest : AbstractSymbolProcessorTest() {
    override fun commonImports(): String {
        return super.commonImports() + """
            import io.koraframework.kafka.common.producer.TransactionalPublisher
            import io.koraframework.kafka.common.annotation.KafkaPublisher
            import io.koraframework.kafka.common.annotation.KafkaPublisher.Topic
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
        clazz.getConstructor(KafkaPublisherTelemetryFactory::class.java, KafkaPublisherTelemetryConfig::class.java, Properties::class.java, Serializer::class.java)
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
        clazz.getConstructor(KafkaPublisherTelemetryFactory::class.java, KafkaPublisherTelemetryConfig::class.java, Properties::class.java, Serializer::class.java)
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
        val m = clazz.getMethod("testProducer_PublisherFactory", KafkaPublisherTelemetryFactory::class.java, KafkaPublisherConfig::class.java, Serializer::class.java, Serializer::class.java)
        assertThat(m).isNotNull()
        assertThat(m.parameters[2].getAnnotationsByType(Tag::class.java)).isNotEmpty()
        assertThat(m.parameters[2].getAnnotationsByType(Tag::class.java)[0].value).isEqualTo(String::class)
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
        val m = clazz.getMethod("testProducer_PublisherFactory", KafkaPublisherTelemetryFactory::class.java, KafkaPublisherConfig::class.java, Serializer::class.java, Serializer::class.java)
        assertThat(m).isNotNull()
        assertThat(m.parameters[2].getAnnotationsByType(Tag::class.java)).isEmpty()
        assertThat(m.parameters[3].getAnnotationsByType(Tag::class.java)).isNotEmpty()
        assertThat(m.parameters[3].getAnnotationsByType(Tag::class.java)[0].value).isEqualTo(String::class)
    }

    @Test
    fun testPublisherWithValue() {
        compile0(
            """
            @io.koraframework.kafka.common.annotation.KafkaPublisher("test")
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
            KafkaPublisherTelemetryFactory::class.java,
            KafkaPublisherTelemetryConfig::class.java,
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
            KafkaPublisherTelemetryFactory::class.java,
            KafkaPublisherTelemetryConfig::class.java,
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
            KafkaPublisherTelemetryFactory::class.java,
            KafkaPublisherTelemetryConfig::class.java,
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
            KafkaPublisherTelemetryFactory::class.java,
            KafkaPublisherConfig::class.java,
            loadClass("\$TestProducer_TopicConfig"),
            Serializer::class.java
        )
        assertThat(m).isNotNull()
        assertThat(m.parameters[3].getAnnotationsByType(Tag::class.java)).isNotEmpty()
        assertThat(m.parameters[3].getAnnotationsByType(Tag::class.java)[0].value).isEqualTo(String::class)
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
            KafkaPublisherTelemetryFactory::class.java,
            KafkaPublisherTelemetryConfig::class.java,
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
            KafkaPublisherTelemetryFactory::class.java,
            KafkaPublisherTelemetryConfig::class.java,
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
            KafkaPublisherTelemetryFactory::class.java,
            KafkaPublisherConfig::class.java,
            loadClass("\$TestProducer_TopicConfig"),
            Serializer::class.java,
            Serializer::class.java
        )
        assertThat(m).isNotNull()
        assertThat(m.parameters[3].getAnnotationsByType(Tag::class.java)).isEmpty()
        assertThat(m.parameters[4].getAnnotationsByType(Tag::class.java)).isNotEmpty()
        assertThat(m.parameters[4].getAnnotationsByType(Tag::class.java)[0].value).isEqualTo(String::class)
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
            KafkaPublisherTelemetryFactory::class.java,
            KafkaPublisherTelemetryConfig::class.java,
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
            KafkaPublisherTelemetryFactory::class.java,
            KafkaPublisherTelemetryConfig::class.java,
            Properties::class.java,
            loadClass("\$TestProducer_TopicConfig"),
            Serializer::class.java
        )
    }

    @Test
    fun testReturnCompletableFuture() {
        compile0(
            """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic("test.sendTopic")
              fun send(value: String): java.util.concurrent.CompletableFuture<*>
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = loadClass("\$TestProducer_Impl")
        assertThat(clazz).isNotNull()
        clazz.getConstructor(
            KafkaPublisherTelemetryFactory::class.java,
            KafkaPublisherTelemetryConfig::class.java,
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
            KafkaPublisherTelemetryFactory::class.java,
            KafkaPublisherTelemetryConfig::class.java,
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
            KafkaPublisherTelemetryFactory::class.java,
            KafkaPublisherTelemetryConfig::class.java,
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
            KafkaPublisherTelemetryFactory::class.java,
            KafkaPublisherTelemetryConfig::class.java,
            Properties::class.java,
            loadClass("\$TestProducer_TopicConfig"),
            Serializer::class.java
        )
    }

    @Test
    fun testDeferred() {
        compile0(
            """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic("test.sendTopic")
              fun send(value: String): kotlinx.coroutines.Deferred<*>
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
    }


    @Test
    fun testAop() {
        compile0(
            """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic("test.sendTopic")
              @io.koraframework.logging.common.annotation.Log
              fun send(value: String)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
    }

    @Test
    fun testPublisherWithRecordMethodThenTopicMethod() {
        compile0(
            """
            @KafkaPublisher("test")
            interface TestProducer {
              fun send(record: ProducerRecord<String, String>)
              @Topic("test.sendTopic")
              fun send(key: String, value: String)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val topicConfig = loadClass("\$TestProducer_TopicConfig")
        assertThat(topicConfig.constructors[0].parameterCount).isEqualTo(1)
        topicConfig.getMethod("getTopic1")
    }

    @Test
    fun testPublisherWithTopicMethodThenRecordMethod() {
        compile0(
            """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic("test.sendTopic")
              fun send(key: String, value: String)
              fun send(record: ProducerRecord<String, String>)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val topicConfig = loadClass("\$TestProducer_TopicConfig")
        assertThat(topicConfig.constructors[0].parameterCount).isEqualTo(1)
        topicConfig.getMethod("getTopic0")
    }

    @Test
    fun testSyncSendFailureThrowsDriverException() {
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
        val producer = mockProducer()
        Mockito.`when`(producer.send(Mockito.any(), Mockito.any())).thenAnswer {
            val ex = TimeoutException("Expiring 1 record(s)")
            it.getArgument<Callback>(1).onCompletion(null, ex)
            CompletableFuture.failedFuture<Any>(ex)
        }
        val publisher = newPublisher(CopyOnWriteArrayList(), producer, newTopicConfig(), StringSerializer())

        val send = publisher.javaClass.getMethod("send", String::class.java)
        assertThatThrownBy { send.invoke(publisher, "v") }
            .cause()
            .isInstanceOfAny(KafkaPublishException::class.java, TimeoutException::class.java)
    }

    @Test
    fun testSerializationFailureEndsObservation() {
        compile0(
            """
            @KafkaPublisher("test")
            interface TestProducer {
              fun send(record: ProducerRecord<String, String>)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val calls = CopyOnWriteArrayList<String>()
        val failing = Serializer<String> { _, _ -> throw IllegalArgumentException("cannot serialize") }
        val publisher = newPublisher(calls, mockProducer(), null, failing)

        val send = publisher.javaClass.getMethod("send", ProducerRecord::class.java)
        assertThatThrownBy { send.invoke(publisher, ProducerRecord("topic", "k", "v")) }
            .cause()
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("cannot serialize")
        assertThat(calls).containsSubsequence("observeError", "end").doesNotContain("onCompletion")
    }

    @Test
    fun testAsyncSendFailureCompletesFutureExceptionally() {
        compile0(
            """
            @KafkaPublisher("test")
            interface TestProducer {
              @Topic("test.sendTopic")
              fun send(value: String): java.util.concurrent.CompletableFuture<RecordMetadata>
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val calls = CopyOnWriteArrayList<String>()
        val producer = mockProducer()
        Mockito.`when`(producer.send(Mockito.any(), Mockito.any())).thenThrow(IllegalStateException("Cannot perform operation after producer has been closed"))
        val publisher = newPublisher(calls, producer, newTopicConfig(), StringSerializer())

        val send = publisher.javaClass.getMethod("send", String::class.java)
        val result = try {
            send.invoke(publisher, "v") as CompletableFuture<*>
        } catch (e: InvocationTargetException) {
            throw AssertionError("send threw instead of completing the future exceptionally", e.cause)
        }
        assertThat(result).isCompletedExceptionally()
        assertThat(calls).containsSubsequence("observeError", "end")
    }

    @Suppress("UNCHECKED_CAST")
    private fun mockProducer() = Mockito.mock(Producer::class.java) as Producer<ByteArray, ByteArray>

    private fun newTopicConfig(): Any {
        val topic = object : KafkaPublisherConfig.TopicConfig {
            override fun topic() = "test-topic"
            override fun partition(): Int? = null
        }
        return loadClass("\$TestProducer_TopicConfig").constructors[0].newInstance(topic)
    }

    private fun newPublisher(calls: MutableList<String>, producer: Producer<ByteArray, ByteArray>, topicConfig: Any?, serializer: Serializer<String>): Any {
        val observation = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(KafkaPublisherRecordObservation::class.java)) { _, m, _ ->
            calls.add(m.name)
            if (m.name == "span") Span.getInvalid() else null
        } as KafkaPublisherRecordObservation
        val telemetry = Mockito.mock(KafkaPublisherTelemetry::class.java)
        Mockito.`when`(telemetry.observeSend(Mockito.anyString())).thenReturn(observation)
        val factory = KafkaPublisherTelemetryFactory { _, _, _, _ -> telemetry }
        val telemetryConfig = Mockito.mock(KafkaPublisherTelemetryConfig::class.java, Mockito.RETURNS_DEEP_STUBS)
        val args = listOfNotNull(factory, telemetryConfig, Properties(), topicConfig, serializer).toTypedArray()
        val publisher = loadClass("\$TestProducer_Impl").constructors[0].newInstance(*args)
        val delegate = AbstractPublisher::class.java.getDeclaredField("delegate")
        delegate.isAccessible = true
        delegate.set(publisher, producer)
        return publisher
    }
}
