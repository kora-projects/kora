package io.koraframework.kafka.symbol.processor.consumer

import org.apache.kafka.common.serialization.Deserializer
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import io.koraframework.common.Tag
import io.koraframework.kafka.common.consumer.ConsumerAwareRebalanceListener
import io.koraframework.kafka.common.consumer.KafkaListenerConfig
import io.koraframework.kafka.common.consumer.containers.handlers.KafkaRecordsHandler
import io.koraframework.kafka.common.consumer.telemetry.KafkaConsumerTelemetryFactory

class KafkaListenerRecordsTest : AbstractKafkaListenerAnnotationProcessorTest() {
    @Test
    fun testProcessRecords() {
        compile(
            """
            class KafkaListenerClass {
                @KafkaListener("test.config.path")
                fun process(event: ConsumerRecords<ByteArray, String>) {
                }
            }
            
            """.trimIndent()
        )
    }

    @Test
    fun testProcessRecordsSuspend() {
        compile(
            """
            class KafkaListenerClass {
                @KafkaListener("test.config.path")
                suspend fun process(event: ConsumerRecords<ByteArray, String>) {
                }
            }
            
            """.trimIndent()
        )
    }

    @Test
    @Disabled("Is not supported by ksp yet")
    fun testProcessRecordsWithTags() {
        compile(
            """
            class KafkaListenerClass {
                @KafkaListener("test.config.path")
                fun process(event: ConsumerRecords<@Tag(KafkaListener::class) String, @Tag(String::class) String>) {
                }
            }
            """.trimIndent()
        )

        val module = loadClass("KafkaListenerModule")
        val container = module.getMethod(
            "kafkaListenerProcessContainer",
            KafkaListenerConfig::class.java,
            KafkaRecordsHandler::class.java,
            Deserializer::class.java,
            Deserializer::class.java,
            KafkaConsumerTelemetryFactory::class.java,
            ConsumerAwareRebalanceListener::class.java
        )
        val keyDeserializer = container.parameters[2]
        val valueDeserializer = container.parameters[3]

        val keyTag = keyDeserializer.getAnnotation(Tag::class.java)
        val valueTag = valueDeserializer.getAnnotation(Tag::class.java)

        Assertions.assertThat(keyTag).isNotNull()
        Assertions.assertThat(keyTag.value.java).isEqualTo(loadClass("KafkaListener"))
        Assertions.assertThat(valueTag).isNotNull()
        Assertions.assertThat(valueTag.value.java).isEqualTo(loadClass("KafkaListener"))
    }

    @Test
    fun testProcessRecordsAnyKeyType() {
        compile(
            """
            class KafkaListenerClass {
                @KafkaListener("test.config.path")
                fun process(event: ConsumerRecords<*, String>) {
                }
            }
            
            """.trimIndent()
        )
    }

    @Test
    fun testProcessRecordsAndConsumer() {
        compile(
            """
            class KafkaListenerClass {
                @KafkaListener("test.config.path")
                fun process(consumer: Consumer<*, *>, event: ConsumerRecords<String, String>) {
                }
            }
            
            """.trimIndent()
        )
    }
}
