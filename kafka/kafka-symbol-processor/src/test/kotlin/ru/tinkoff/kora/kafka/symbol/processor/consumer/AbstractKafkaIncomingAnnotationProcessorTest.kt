package ru.tinkoff.kora.kafka.symbol.processor.consumer

import org.intellij.lang.annotations.Language
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import ru.tinkoff.kora.ksp.common.KotlinCompilation

abstract class AbstractKafkaListenerAnnotationProcessorTest : AbstractSymbolProcessorTest() {
    override fun commonImports(): String {
        return super.commonImports() + """
            import ru.tinkoff.kora.kafka.common.annotation.KafkaListener;
            import org.apache.kafka.clients.consumer.ConsumerRecords;
            import org.apache.kafka.clients.consumer.ConsumerRecord;
            import org.apache.kafka.clients.consumer.Consumer;
            import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerTelemetry;
            import ru.tinkoff.kora.kafka.common.exceptions.RecordKeyDeserializationException;
            import ru.tinkoff.kora.kafka.common.exceptions.RecordValueDeserializationException;
            import org.apache.kafka.common.header.Headers;
            """.trimIndent()
    }

    fun compile(@Language("kotlin") vararg sources: String) = KotlinCompilation()
        .withClasspathJar("kafka-clients")
        .compile(listOf(KafkaListenerSymbolProcessorProvider()), *sources).apply {
            compileResult.assertSuccess()
        }


}
