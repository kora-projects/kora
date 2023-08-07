package ru.tinkoff.kora.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ru.tinkoff.kora.resilient.timeout.TimeoutExhaustedException
import ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KspExperimental
class TimeoutTests : AppRunner() {

    private inline fun <reified T> getService(): T {
        val graph = getGraphForApp(
            AppWithConfig::class,
            listOf(
                CircuitBreakerTarget::class,
                FallbackTarget::class,
                RetryTarget::class,
                TimeoutTarget::class,
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
    fun suspendTimeout() {
        // given
        val service = getService<TimeoutTarget>()

        // then
        assertThrows(TimeoutExhaustedException::class.java) { runBlocking { service.getValueSuspend() } }
    }

    @Test
    fun flowTimeout() {
        // given
        val service = getService<TimeoutTarget>()

        // when
        assertThrows(TimeoutExhaustedException::class.java) { runBlocking { service.getValueFLow().firstOrNull() } }
    }
}
