package io.koraframework.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import io.koraframework.resilient.symbol.processor.aop.testdata.AppWithConfig
import io.koraframework.resilient.symbol.processor.aop.testdata.TimeoutTarget
import io.koraframework.resilient.symbol.processor.aop.testdata.`typealias`.TimeoutAliasTarget
import io.koraframework.resilient.timeout.TimeoutExhaustedException

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KspExperimental
class TimeoutTests : AppRunner() {

    private inline fun <reified T> getService(): T {
        val graph = getGraphForApp(
            AppWithConfig::class,
            listOf(
                TimeoutTarget::class,
                TimeoutAliasTarget::class,
            )
        )

        return getServiceFromGraph(graph)
    }

    @Test
    fun syncTimeout() {
        // given
        val service = getService<TimeoutTarget>()
        assertThrows(TimeoutExhaustedException::class.java) { service.getValueSync() }
    }

    @Test
    fun aliasAnnotation() {
        // given
        val service = getService<TimeoutAliasTarget>()
        assertThrows(TimeoutExhaustedException::class.java) { service.getValueSync() }
    }
}
