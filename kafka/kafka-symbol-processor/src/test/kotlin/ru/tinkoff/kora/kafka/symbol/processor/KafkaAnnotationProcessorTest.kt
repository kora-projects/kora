package ru.tinkoff.kora.kafka.symbol.processor

import com.google.devtools.ksp.KspExperimental
import com.typesafe.config.Config
import org.apache.kafka.common.serialization.Deserializer
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.annotation.processor.common.MethodAssertUtils
import ru.tinkoff.kora.application.graph.TypeRef
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor
import ru.tinkoff.kora.kafka.common.config.KafkaConsumerConfig
import ru.tinkoff.kora.kafka.common.containers.KafkaConsumerContainer
import ru.tinkoff.kora.kafka.common.telemetry.KafkaConsumerTelemetry
import ru.tinkoff.kora.ksp.common.symbolProcess
import java.lang.reflect.Type
import java.lang.reflect.Modifier
import java.util.*
import kotlin.reflect.KClass

@KspExperimental
class KafkaAnnotationProcessorTest {
    @Test
    @Throws(Exception::class)
    fun testKafkaAnnotationProcessor() {
        val module = createModule(KafkaConsumersComponent::class)
        assertConsumer(module, "processRecordWithConsumer", String::class.java, String::class.java)
        assertConsumer(module, "processRecords", String::class.java, String::class.java)
        assertConsumer(module, "processRecordsWithConsumer", String::class.java, String::class.java)
        assertConsumer(module, "processRecord", String::class.java, String::class.java)
        assertConsumer(module, "processValue", ByteArray::class.java, String::class.java)
        assertConsumer(module, "processKeyValueWithException", String::class.java, String::class.java)
        assertConsumer(module, "processValueWithException", ByteArray::class.java, String::class.java)
    }

    private fun assertConsumer(module: Class<*>, methodName: String, key: Type, value: Type) {
        val configName = "kafkaConsumersComponent" + methodName.substring(0, 1).uppercase(Locale.getDefault()) + methodName.substring(1) + "Config"
        val consumerName = "kafkaConsumersComponent" + methodName.substring(0, 1).uppercase(Locale.getDefault()) + methodName.substring(1)
        MethodAssertUtils.assertHasMethod(
            module, configName, KafkaConsumerConfig::class.java,
            Config::class.java,
            TypeRef.of(ConfigValueExtractor::class.java, TypeRef.of(KafkaConsumerConfig::class.java))
        )
        MethodAssertUtils.assertHasMethod(
            module, consumerName, TypeRef.of(KafkaConsumerContainer::class.java, key, value),
            KafkaConsumersComponent::class.java,
            KafkaConsumerConfig::class.java,
            TypeRef.of(Deserializer::class.java, key),
            TypeRef.of(Deserializer::class.java, value),
            TypeRef.of(KafkaConsumerTelemetry::class.java, key, value)
        )
    }


    @Throws(Exception::class)
    private fun <T : Any> createModule(targetClass: KClass<T>): Class<*> {
        return try {
            val classLoader = symbolProcess(targetClass, KafkaSymbolProcessorProvider())
            classLoader.loadClass(targetClass.qualifiedName + "Module")
        } catch (e: Exception) {
            if (e.cause != null) {
                throw (e.cause as Exception?)!!
            }
            throw e
        }
    }
}
