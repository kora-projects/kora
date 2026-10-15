package io.koraframework.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import io.koraframework.ksp.common.exception.ProcessingErrorException
import io.koraframework.resilient.bulkhead.Bulkhead
import io.koraframework.resilient.bulkhead.exception.BulkheadFullException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CompletableFuture

@KspExperimental
class BulkheadTests : ResilientAopSymbolTestSupport() {
    override fun commonImports(): String = super.commonImports() + """
        import io.koraframework.resilient.bulkhead.annotation.Bulkheaded
        import io.koraframework.resilient.bulkhead.annotation.BulkheadSpec
        import kotlinx.coroutines.*
        import kotlinx.coroutines.flow.*
        import java.util.concurrent.CompletableFuture
        import java.util.concurrent.CompletionStage
    """

    private fun target(methods: String): Any = target("orders { maxConcurrentCalls = 1 }", methods)

    private fun target(config: String, methods: String): Any = compileApp(
        config, """
        @BulkheadSpec("orders")
        interface OrdersBulkhead : io.koraframework.resilient.bulkhead.Bulkhead
    """, """
        @Component
        @Root
        open class TestTarget(private val bulkhead: OrdersBulkhead, private val sameBulkhead: OrdersBulkhead) {
            fun limiter(): OrdersBulkhead = bulkhead
            fun sameLimiter(): OrdersBulkhead = sameBulkhead
            $methods
        }
    """
    )

    @Test
    fun queuedCoroutineCancellationAndGrantRaceReleaseCapacity() {
        val service = target(
            """
            orders {
                maxConcurrentCalls = 2
                type = AIMD
                adaptive { initialLimit = 1, samplingInterval = 1ns, minimumSamples = 1 }
                maxQueuedCalls = 1
                maxWaitDuration = 10s
            }
        """, """
            var started = 0
            @Bulkheaded(OrdersBulkhead::class)
            open suspend fun value(): Int { started++; return 1 }
            fun runQueued(): Int = runBlocking {
                repeat(100) {
                    val held = bulkhead.acquire()
                    held.observeError(CancellationException("Control permit"))
                    val waiting = async(start = CoroutineStart.UNDISPATCHED) { value() }
                    check(bulkhead.queueLength() == 1)
                    if (it % 2 == 0) {
                        waiting.cancelAndJoin()
                        check(bulkhead.queueLength() == 0)
                        held.close()
                    } else {
                        held.close()
                        waiting.cancelAndJoin()
                    }
                    check(bulkhead.inFlight() == 0)
                    check(bulkhead.currentLimit() == 1)
                }
                check(started == 0)
                value()
            }
        """
        )
        val limiter = call(service, "limiter") as Bulkhead
        try {
            assertEquals(1, call(service, "runQueued"))
            assertEquals(0, limiter.inFlight())
            assertEquals(0, limiter.queueLength())
        } finally {
            (limiter as io.koraframework.application.graph.Lifecycle).release()
        }
    }

    @Test
    fun adaptiveSyncSuspendAndFlowKeepCallersContext() {
        val service = target(
            """
            orders {
                maxConcurrentCalls = 2
                type = THROUGHPUT
                adaptive { initialLimit = 1 }
                maxQueuedCalls = 1
                maxWaitDuration = 10s
            }
        """, """
            var lastThread = 0L
            @Bulkheaded(OrdersBulkhead::class)
            open fun sync(): Long = Thread.currentThread().threadId()
            @Bulkheaded(OrdersBulkhead::class)
            open fun unit() { lastThread = Thread.currentThread().threadId() }
            fun last(): Long = lastThread
            @Bulkheaded(OrdersBulkhead::class)
            open suspend fun suspended(): Long { yield(); return Thread.currentThread().threadId() }
            @Bulkheaded(OrdersBulkhead::class)
            open fun values(): Flow<Long> = flow { yield(); emit(Thread.currentThread().threadId()) }
            fun runSuspend(): Long = runBlocking { suspended() }
            fun runFlow(): Long = runBlocking { values().first() }
        """
        )
        val limiter = call(service, "limiter") as Bulkhead
        try {
            assertEquals(1, limiter.currentLimit())
            assertEquals(2, limiter.maxConcurrentCalls())
            for (method in listOf("sync", "runSuspend", "runFlow")) {
                assertEquals(Thread.currentThread().threadId(), call(service, method))
                assertEquals(0, limiter.inFlight())
            }
            call(service, "unit")
            assertEquals(Thread.currentThread().threadId(), call(service, "last"))
        } finally {
            (limiter as io.koraframework.application.graph.Lifecycle).release()
        }
    }

    @Test
    fun cancellationWaitsForCoroutineCleanup() {
        val service = target(
            """
            orders { maxConcurrentCalls = 1 }
        """, """
            val started = CompletableDeferred<Unit>()
            @Bulkheaded(OrdersBulkhead::class)
            open suspend fun value() {
                started.complete(Unit)
                try { awaitCancellation() }
                finally {
                    withContext(NonCancellable) {
                        delay(10)
                        check(bulkhead.inFlight() == 1)
                    }
                }
            }
            fun runCancelled(): Int = runBlocking {
                val running = launch { value() }
                started.await()
                running.cancelAndJoin()
                bulkhead.inFlight()
            }
        """
        )
        val limiter = call(service, "limiter") as Bulkhead
        try {
            assertEquals(0, call(service, "runCancelled"))
        } finally {
            (limiter as io.koraframework.application.graph.Lifecycle).release()
        }
    }

    @Test
    fun syncAndUnitMethodsShareTypedBudget() {
        val service = target(
            """
            @Bulkheaded(OrdersBulkhead::class)
            open fun value(input: String): String = input
            @Bulkheaded(OrdersBulkhead::class)
            open fun unit() {}
        """
        )
        val limiter = call(service, "limiter") as Bulkhead
        assertSame(limiter, call(service, "sameLimiter"))
        assertEquals("ok", call(service, "value", "ok"))
        call(service, "unit")
        assertEquals(0, limiter.inFlight())
        limiter.acquire().use {
            assertThrows<BulkheadFullException> { call(service, "value", "no") }
            assertEquals(1, limiter.inFlight())
        }
    }

    @Test
    fun errorsAndCoroutineCancellationReleasePermit() {
        val service = target(
            """
            @Bulkheaded(OrdersBulkhead::class)
            open fun failure(): String = throw IOException("failed")
            @Bulkheaded(OrdersBulkhead::class)
            open suspend fun cancelled(): String {
                yield()
                throw CancellationException("cancelled")
            }
            fun runCancelled(): String = runBlocking { cancelled() }
        """
        )
        val limiter = call(service, "limiter") as Bulkhead
        assertThrows<java.io.IOException> { call(service, "failure") }
        assertEquals(0, limiter.inFlight())
        assertThrows<java.util.concurrent.CancellationException> { call(service, "runCancelled") }
        assertEquals(0, limiter.inFlight())
    }

    @Test
    fun suspendMethodKeepsPermitAcrossSuspension() {
        val service = target(
            """
            @Bulkheaded(OrdersBulkhead::class)
            open suspend fun value(): Int {
                yield()
                check(bulkhead.inFlight() == 1)
                yield()
                return bulkhead.inFlight()
            }
            fun runValue(): Int = runBlocking { value() }
        """
        )
        assertEquals(1, call(service, "runValue"))
        assertEquals(0, (call(service, "limiter") as Bulkhead).inFlight())
    }

    @Test
    fun flowAcquiresPerCollectionAndReleasesOnEarlyCancellation() {
        val service = target(
            """
            @Bulkheaded(OrdersBulkhead::class)
            open fun values(): Flow<Int> = flow {
                check(bulkhead.inFlight() == 1)
                emit(1)
                emit(2)
            }
            fun collectFirst(): Int = runBlocking { values().first() }
            fun collectAll(): List<Int> = runBlocking { values().toList() }
        """
        )
        val limiter = call(service, "limiter") as Bulkhead
        assertNotNull(call(service, "values"))
        assertEquals(0, limiter.inFlight())
        limiter.acquire().use {
            assertNotNull(call(service, "values"))
            assertThrows<BulkheadFullException> { call(service, "collectAll") }
        }
        assertEquals(1, call(service, "collectFirst"))
        assertEquals(0, limiter.inFlight())
        assertEquals(listOf(1, 2), call(service, "collectAll"))
        assertEquals(0, limiter.inFlight())
    }

    @Test
    fun completableFutureKeepsSourcePermitWhenResultIsCancelled() {
        val service = target(
            """
            @Bulkheaded(OrdersBulkhead::class)
            open fun future(source: CompletableFuture<String>): CompletableFuture<String> = source
            @Bulkheaded(OrdersBulkhead::class)
            open fun stage(source: CompletableFuture<String>): CompletionStage<String> = source
        """
        )
        val limiter = call(service, "limiter") as Bulkhead
        val source = CompletableFuture<String>()
        val result = call(service, "future", source) as CompletableFuture<*>
        assertEquals(1, limiter.inFlight())
        result.cancel(false)
        assertFalse(source.isCancelled)
        assertThrows<BulkheadFullException> { call(service, "stage", source) }
        source.complete("ok")
        assertEquals(0, limiter.inFlight())
        val stage = call(service, "stage", CompletableFuture.completedFuture("done")) as java.util.concurrent.CompletionStage<*>
        assertEquals("done", stage.toCompletableFuture().join())
        assertEquals(0, limiter.inFlight())
    }

    @Test
    fun unsupportedFutureHasDiagnostic() {
        compileFailed(
            app("orders { maxConcurrentCalls = 1 }"), """
            @BulkheadSpec("orders")
            interface OrdersBulkhead : io.koraframework.resilient.bulkhead.Bulkhead
        """, """
            @Component
            @Root
            open class TestTarget {
                @Bulkheaded(OrdersBulkhead::class)
                open fun call(): java.util.concurrent.Future<String>? = null
            }
        """
        )
        assertTrue(compileResult.assertFailure().messages.any { it.contains("@Bulkheaded") })
    }

    @Test
    fun invalidSpecHasDiagnostic() {
        val error = assertThrows<ProcessingErrorException> {
            compileFailed(
                """
            @BulkheadSpec("orders")
            interface InvalidBulkhead
        """
            )
        }
        assertTrue(error.message!!.contains("must extend"))
    }

    @Test
    fun blankConfigPathHasDiagnostic() {
        val error = assertThrows<ProcessingErrorException> {
            compileFailed(
                """
            @BulkheadSpec(" ")
            interface InvalidBulkhead : io.koraframework.resilient.bulkhead.Bulkhead
        """
            )
        }
        assertTrue(error.message!!.contains("blank config path"))
    }
}
