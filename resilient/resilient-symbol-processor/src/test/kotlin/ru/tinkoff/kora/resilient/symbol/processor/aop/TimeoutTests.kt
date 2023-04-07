package ru.tinkoff.kora.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.*
import ru.tinkoff.kora.resilient.timeout.TimeoutException

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KspExperimental
class TimeoutTests : AppRunner() {

    private inline fun <reified T> getService(): T {
        val graph = getGraphForApp(
            AppWithConfig::class,
            listOf(
                CircuitBreakerTarget::class,
                FallbackTarget::class,
                RetryableTarget::class,
                TimeoutTarget::class,
            )
        )

        return getServiceFromGraph(graph)
    }

    @Test
    fun syncTimeout() {
        // given
        val service = getService<TimeoutTarget>()
        assertThrows(TimeoutException::class.java) { service.getValueSync() }
    }

    @Test
    fun suspendTimeout() {
        // given
        val service = getService<TimeoutTarget>()

        // then
        assertThrows(TimeoutException::class.java) { runBlocking { service.getValueSuspend() } }
    }

    @Test
    fun flowTimeout() {
        // given
        val service = getService<TimeoutTarget>()

        // when
        assertThrows(TimeoutException::class.java) { runBlocking { service.getValueFLow().firstOrNull() } }
    }
}
