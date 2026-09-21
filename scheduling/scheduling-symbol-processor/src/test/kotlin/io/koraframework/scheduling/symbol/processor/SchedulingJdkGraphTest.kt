package io.koraframework.scheduling.symbol.processor

import io.koraframework.config.ksp.processor.ConfigParserSymbolProcessorProvider
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SchedulingJdkGraphTest : AbstractSymbolProcessorTest() {

    @Test
    fun jdkJobIsResolvedTogetherWithItsExecutor() {
        compile0(
            listOf(KoraAppProcessorProvider(), SchedulingSymbolProcessorProvider(), ConfigParserSymbolProcessorProvider()),
            """
            @KoraApp
            interface JobApplication : io.koraframework.scheduling.jdk.SchedulingJdkModule, io.koraframework.config.common.mapper.ConfigValueMapperModule {
              fun config(): io.koraframework.config.common.Config = TODO()
            }
            
            """.trimIndent(),
            """
            @Component
            class SomeJob {
              @io.koraframework.scheduling.jdk.annotation.ScheduleAtFixedRate(period = 1000)
              fun run() {}
            }
            
            """.trimIndent()
        )

        compileResult.assertSuccess()
        assertThat(loadClass("JobApplicationGraph")).isNotNull()
    }
}
