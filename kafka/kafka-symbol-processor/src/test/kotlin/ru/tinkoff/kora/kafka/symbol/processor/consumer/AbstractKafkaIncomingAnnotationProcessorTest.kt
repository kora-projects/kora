package ru.tinkoff.kora.kafka.symbol.processor.consumer

import org.intellij.lang.annotations.Language
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import java.util.*

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
            
            """.trimIndent()
    }


    protected fun compile(@Language("kotlin") vararg sources: String) {
        super.compile(listOf(KafkaListenerSymbolProcessorProvider()), *sources)
        compileResult.assertSuccess()
//        val kafkaListenerClass = Objects.requireNonNull(compileResult.loadClass("KafkaListener"))
//        val kafkaListenerModule = Objects.requireNonNull(compileResult.loadClass("KafkaListenerModule"))
    }


}
