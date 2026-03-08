package io.koraframework.kafka.symbol.processor.consumer

import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import org.intellij.lang.annotations.Language

abstract class AbstractKafkaListenerAnnotationProcessorTest : AbstractSymbolProcessorTest() {
    override fun commonImports(): String {
        return super.commonImports() + """
            import io.koraframework.kafka.common.annotation.KafkaListener;
            import org.apache.kafka.clients.consumer.ConsumerRecords;
            import org.apache.kafka.clients.consumer.ConsumerRecord;
            import org.apache.kafka.clients.consumer.Consumer;
            import io.koraframework.kafka.common.consumer.telemetry.KafkaConsumerTelemetry;
            import io.koraframework.kafka.common.exceptions.RecordKeyDeserializationException;
            import io.koraframework.kafka.common.exceptions.RecordValueDeserializationException;
            import org.apache.kafka.common.header.Headers;
            """.trimIndent()
    }


    protected fun compile(@Language("kotlin") vararg sources: String) {
        super.compile0(listOf(KafkaListenerSymbolProcessorProvider()), *sources)
        compileResult.assertSuccess()
//        val kafkaListenerClass = Objects.requireNonNull(compileResult.loadClass("KafkaListener"))
//        val kafkaListenerModule = Objects.requireNonNull(compileResult.loadClass("KafkaListenerModule"))
    }


}
