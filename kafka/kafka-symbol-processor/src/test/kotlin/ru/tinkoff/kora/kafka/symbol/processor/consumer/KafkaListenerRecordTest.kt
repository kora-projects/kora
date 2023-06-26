package ru.tinkoff.kora.kafka.symbol.processor.consumer

import org.apache.kafka.common.serialization.Deserializer
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.kafka.common.config.KafkaConsumerConfig
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.KafkaRecordHandler
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerTelemetry

class KafkaListenerRecordTest : AbstractKafkaListenerAnnotationProcessorTest() {
    @Test
    fun testProcessRecord() {
        compile("""
            class KafkaListenerClass {
                @KafkaListener("test.config.path")
                fun process(event: ConsumerRecord<String, String>) {
                }
            }
            
            """.trimIndent())
    }

    @Test
    fun testProcessRecordSuspend() {
        compile("""
            class KafkaListenerClass {
                @KafkaListener("test.config.path")
                suspend fun process(event: ConsumerRecord<String, String>) {
                }
            }
            
            """.trimIndent())
    }

    @Test
    @Disabled("Is not supported by ksp yet")
    fun testProcessRecordWithTags() {
        compile("""
            class KafkaListenerClass {
                @KafkaListener("test.config.path")
                fun process(event: ConsumerRecord<@Tag(KafkaListener::class) String, @Tag(String::class) String>) {
                }
            }
            """.trimIndent())

        val module = compileResult.loadClass("KafkaListenerModule")
        val container = module.getMethod("kafkaListenerProcessContainer", KafkaConsumerConfig::class.java, KafkaRecordHandler::class.java, Deserializer::class.java, Deserializer::class.java, KafkaConsumerTelemetry::class.java)
        val keyDeserializer = container.parameters[2]
        val valueDeserializer = container.parameters[3]

        val keyTag = keyDeserializer.getAnnotation(Tag::class.java)
        val valueTag = valueDeserializer.getAnnotation(Tag::class.java)

        Assertions.assertThat(keyTag).isNotNull()
        Assertions.assertThat(keyTag.value.map { it.java }).isEqualTo(listOf(compileResult.loadClass("KafkaListener")))
        Assertions.assertThat(valueTag).isNotNull()
        Assertions.assertThat(valueTag.value.map { it.java }).isEqualTo(listOf(compileResult.loadClass("KafkaListener")))
    }

    @Test
    fun testProcessRecordAnyKeyType() {
        compile("""
            class KafkaListenerClass {
                @KafkaListener("test.config.path")
                fun process(event: ConsumerRecord<*, String>) {
                }
            }
            
            """.trimIndent())
    }

    @Test
    fun testProcessRecordAndConsumer() {
        compile("""
            class KafkaListenerClass {
                @KafkaListener("test.config.path")
                fun process(consumer: Consumer<String, String>, event: ConsumerRecord<String, String>) {
                }
            }
            
            """.trimIndent())
    }

    @Test
    fun testProcessRecordAndKeyParseException() {
        compile("""
            class KafkaListenerClass {
                @KafkaListener("test.config.path")
                fun process(event: ConsumerRecord<String, String>?, exception: RecordKeyDeserializationException?) {
                }
            }
            
            """.trimIndent())
    }

    @Test
    fun testProcessRecordAndValueParseException() {
        compile("""
            class KafkaListenerClass {
                @KafkaListener("test.config.path")
                fun process(event: ConsumerRecord<String, String>?, exception: RecordValueDeserializationException?) {
                }
            }
            
            """.trimIndent())
    }

    @Test
    fun testProcessRecordAndParseException() {
        compile("""
            class KafkaListenerClass {
                @KafkaListener("test.config.path")
                fun process(event: ConsumerRecord<String, String>?, exception: Exception?) {
                }
            }
            
            """.trimIndent())
    }

    @Test
    fun testProcessRecordAndParseThrowable() {
        compile("""
            class KafkaListenerClass {
                @KafkaListener("test.config.path")
                fun process(event: ConsumerRecord<String, String>?, exception: Throwable?) {
                }
            }
            
            """.trimIndent())
    }
}
