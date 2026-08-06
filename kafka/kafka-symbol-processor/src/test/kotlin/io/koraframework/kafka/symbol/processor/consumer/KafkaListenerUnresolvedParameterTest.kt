package io.koraframework.kafka.symbol.processor.consumer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import io.koraframework.ksp.common.AbstractSymbolProcessorTest

class KafkaListenerUnresolvedParameterTest : AbstractSymbolProcessorTest() {

    override fun commonImports(): String {
        return super.commonImports() + """
            import io.koraframework.kafka.common.annotation.KafkaListener;
            """.trimIndent()
    }

    @Test
    fun listenerWithUnresolvedParameterTypeIsReportedAsDiagnostic() {
        val result = compile0(
            listOf(KafkaListenerSymbolProcessorProvider()),
            """
            @Component
            class KafkaListenerClass {
                @KafkaListener("test.config.path")
                fun process(context: SomeTypeThatDoesNotExist) {
                }
            }
            """.trimIndent()
        ).assertFailure()

        val messages = result.messages.joinToString("\n")
        assertThat(messages).doesNotContain("Required value was null")
        assertThat(messages).contains("SomeTypeThatDoesNotExist")
    }
}
