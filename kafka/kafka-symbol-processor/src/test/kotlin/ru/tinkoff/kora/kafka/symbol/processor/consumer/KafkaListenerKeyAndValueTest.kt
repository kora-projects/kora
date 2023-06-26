package ru.tinkoff.kora.kafka.symbol.processor.consumer

import org.apache.kafka.common.serialization.Deserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.kafka.common.config.KafkaConsumerConfig
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.KafkaRecordHandler
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerTelemetry

class KafkaListenerKeyAndValueTest : AbstractKafkaListenerAnnotationProcessorTest() {
    @Test
    fun testProcessValue() {
        compile("""
            class KafkaListenerClass {
                @KafkaListener("test.config.path")
                fun process(value: String) {
                }
            }
            
            """.trimIndent())
    }

    @Test
    fun testProcessValueSuspend() {
        compile("""
            class KafkaListenerClass {
                @KafkaListener("test.config.path")
                suspend fun process(value: String) {
                }
            }
            
            """.trimIndent())
    }

    @Test
    fun testProcessValueWithTag() {
        compile("""
            class KafkaListenerClass {
                @KafkaListener("test.config.path")
                fun process(@Tag(KafkaListenerClass::class) value: String) {
                }
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        val module = compileResult.loadClass("KafkaListenerClassModule")
        val container = module.getMethod("kafkaListenerClassProcessContainer", KafkaConsumerConfig::class.java, KafkaRecordHandler::class.java, Deserializer::class.java, Deserializer::class.java, KafkaConsumerTelemetry::class.java)
        val valueDeserializer = container.parameters[3]

        val valueTag = valueDeserializer.getAnnotation(Tag::class.java)

        assertThat(valueTag).isNotNull()
        assertThat(valueTag.value.map { it.java }).isEqualTo(listOf(compileResult.loadClass("KafkaListenerClass")))
    }

    @Test
    fun testProcessKeyAndValue() {
        compile("""
            class KafkaListenerClass {
                @KafkaListener("test.config.path")
                fun process(key: String, value: String) {
                }
            }
            
            """.trimIndent())
    }

    @Test
    fun testProcessKeyAndValueWithTag() {
        compile("""
            class KafkaListenerClass {
                @KafkaListener("test.config.path")
                fun process(@Tag(KafkaListenerClass::class) key: String, @Tag(String::class) value: String) {
                }
            }
            
            """.trimIndent())
        val module = compileResult.loadClass("KafkaListenerClassModule")
        val container = module.getMethod("kafkaListenerClassProcessContainer", KafkaConsumerConfig::class.java, KafkaRecordHandler::class.java, Deserializer::class.java, Deserializer::class.java, KafkaConsumerTelemetry::class.java)
        val keyDeserializer = container.parameters[2]
        val valueDeserializer = container.parameters[3]

        val keyTag = keyDeserializer.getAnnotation(Tag::class.java)
        val valueTag = valueDeserializer.getAnnotation(Tag::class.java)

        assertThat(keyTag).isNotNull()
        assertThat(keyTag.value.map { it.java }).isEqualTo(listOf(compileResult.loadClass("KafkaListenerClass")))
        assertThat(valueTag).isNotNull()
        assertThat(valueTag.value.map { it.java }).isEqualTo(listOf(String::class.java))
    }

    @Test
    fun testProcessValueAndValueException() {
        compile("""
            class KafkaListenerClass {
                @KafkaListener("test.config.path")
                fun process(value: String?, exception: RecordValueDeserializationException?) {
                }
            }
            
            """.trimIndent())
    }

    @Test
    fun testProcessValueAndException() {
        compile("""
            class KafkaListenerClass {
                @KafkaListener("test.config.path")
                fun process(value: String?, exception: Exception?) {
                }
            }
            
            """.trimIndent())
    }

    @Test
    fun testProcessValueAndConsumer() {
        compile("""
            class KafkaListenerClass {
                @KafkaListener("test.config.path")
                fun process(consumer: Consumer<*,*>, value: String?, exception: Exception?) {
                }
            }
            
            """.trimIndent())
    }
}
