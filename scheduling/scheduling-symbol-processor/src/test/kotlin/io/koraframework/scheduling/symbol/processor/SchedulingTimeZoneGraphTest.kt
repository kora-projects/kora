package io.koraframework.scheduling.symbol.processor

import io.koraframework.config.ksp.processor.ConfigParserSymbolProcessorProvider
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import io.koraframework.ksp.common.GraphUtil.toGraphDraw
import io.koraframework.scheduling.common.SchedulingModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.ZoneId

class SchedulingTimeZoneGraphTest : AbstractSymbolProcessorTest() {

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun cronJobsUseTaggedTimeZone(zoneComponent: Boolean) {
        val zone = if (zoneComponent) {
            "@Tag(io.koraframework.scheduling.common.SchedulingModule::class) fun zone(): java.time.ZoneId = java.time.ZoneId.of(\"Europe/Moscow\")"
        } else {
            ""
        }
        compile0(
            listOf(KoraAppProcessorProvider(), SchedulingSymbolProcessorProvider(), ConfigParserSymbolProcessorProvider()),
            """
            @KoraApp
            interface JobApplication : io.koraframework.scheduling.jdk.SchedulingJdkModule,
              io.koraframework.scheduling.quartz.QuartzModule,
              io.koraframework.scheduling.db.scheduler.DbSchedulerModule,
              io.koraframework.config.common.mapper.ConfigValueMapperModule {
              fun config(): io.koraframework.config.common.Config = TODO()
              fun dataSource(): javax.sql.DataSource = TODO()
              $zone
            }

            """.trimIndent(),
            """
            @Component
            class SomeJobs {
              @io.koraframework.scheduling.jdk.annotation.ScheduleJdkWithCron("0 0 15 * * ?")
              fun jdk() {}

              @io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithCron("0 0 15 * * ?")
              fun quartz() {}

              @io.koraframework.scheduling.db.scheduler.annotation.ScheduleDbWithCron("0 0 15 * * *")
              fun db() {}
            }

            """.trimIndent()
        )

        compileResult.assertSuccess()
        val draw = loadClass("JobApplicationGraph").toGraphDraw()
        assertThat(draw.findNodesByType(ZoneId::class.java, SchedulingModule::class.java)).hasSize(if (zoneComponent) 1 else 0)
    }
}
