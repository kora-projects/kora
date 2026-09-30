package io.koraframework.scheduling.symbol.processor

import io.koraframework.config.ksp.processor.ConfigParserSymbolProcessorProvider
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import io.koraframework.ksp.common.GraphUtil.toGraphDraw
import io.koraframework.scheduling.db.scheduler.DbSchedulerWrapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SchedulingDbGraphTest : AbstractSymbolProcessorTest() {

    @Test
    fun dbJobIsResolvedTogetherWithDbScheduler() {
        compile0(
            listOf(KoraAppProcessorProvider(), SchedulingSymbolProcessorProvider(), ConfigParserSymbolProcessorProvider()),
            """
            @KoraApp
            interface JobApplication : io.koraframework.scheduling.db.scheduler.DbSchedulerModule, io.koraframework.config.common.mapper.ConfigValueMapperModule {
              fun config(): io.koraframework.config.common.Config = TODO()
              fun dataSource(): javax.sql.DataSource = TODO()
            }
            
            """.trimIndent(),
            """
            @Component
            class SomeJob {
              @io.koraframework.scheduling.db.scheduler.annotation.ScheduleWithFixedDelay(delay = 1000)
              fun run() {}
            }
            
            """.trimIndent()
        )

        compileResult.assertSuccess()
        val draw = loadClass("JobApplicationGraph").toGraphDraw()
        assertThat(draw.findNodeByType(DbSchedulerWrapper::class.java)).isNotNull()
    }
}
