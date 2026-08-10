package io.koraframework.common.concurrent

import io.koraframework.common.Principal
import io.koraframework.common.telemetry.Observation
import io.koraframework.logging.common.MDC
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger

class StructuredTest {
    @Test
    fun `executes blocking operation on virtual thread`() {
        val isVirtual = structured {
            asyncVirtual { Thread.currentThread().isVirtual }.await()
        }

        assertThat(isVirtual).isTrue()
    }

    @Test
    fun `executes children concurrently`() {
        val ready = CountDownLatch(2)

        val results = structured {
            val first = asyncVirtual {
                ready.countDown()
                ready.await(5, TimeUnit.SECONDS)
            }
            val second = asyncVirtual {
                ready.countDown()
                ready.await(5, TimeUnit.SECONDS)
            }
            listOf(first.await(), second.await())
        }

        assertThat(results).containsExactly(true, true)
    }

    @Test
    fun `maps iterable on virtual threads`() {
        val results = structured {
            listOf(1, 2, 3).mapVirtual { value ->
                check(Thread.currentThread().isVirtual)
                value * 2
            }.awaitAll()
        }

        assertThat(results).containsExactly(2, 4, 6)
    }

    @Test
    fun `executes on custom dispatcher with captured context`() {
        val parentMdc = MDC().apply {
            put0("requestId", "request-value")
        }

        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "custom-dispatcher")
        }.asCoroutineDispatcher().use { dispatcher ->
            val result = KoraContextTestHelper.withMdc(parentMdc) {
                structured {
                    asyncOn(dispatcher) {
                        Triple(
                            Thread.currentThread().name,
                            Thread.currentThread().isVirtual,
                            MDC.get().values().keys,
                        )
                    }.await()
                }
            }

            assertThat(result.first).startsWith("custom-dispatcher")
            assertThat(result.second).isFalse()
            assertThat(result.third).contains("requestId")
        }
    }

    @Test
    fun `waits for launched virtual task`() {
        val thread = AtomicReference<Thread>()

        structured {
            launchVirtual {
                Thread.sleep(50)
                thread.set(Thread.currentThread())
            }
        }

        assertThat(thread.get()).isNotNull
        assertThat(thread.get().isVirtual).isTrue()
    }

    @Test
    fun `waits for launched task on custom dispatcher`() {
        val threadName = AtomicReference<String>()

        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "custom-launch-dispatcher")
        }.asCoroutineDispatcher().use { dispatcher ->
            structured {
                launchOn(dispatcher) {
                    threadName.set(Thread.currentThread().name)
                }
            }
        }

        assertThat(threadName.get()).startsWith("custom-launch-dispatcher")
    }

    @Test
    fun `propagates launched task failure`() {
        assertThatThrownBy {
            structured {
                launchVirtual { throw TestException() }
            }
        }.isInstanceOf(TestException::class.java)
    }

    @Test
    fun `maps iterable on custom dispatcher`() {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "custom-map-dispatcher")
        }.asCoroutineDispatcher().use { dispatcher ->
            val results = structured {
                listOf(1, 2, 3).mapOn(dispatcher) { value ->
                    check(Thread.currentThread().name.startsWith("custom-map-dispatcher"))
                    value * 2
                }.awaitAll()
            }

            assertThat(results).containsExactly(2, 4, 6)
        }
    }

    @Test
    fun `limits virtual map parallelism`() {
        val active = AtomicInteger()
        val maximumActive = AtomicInteger()
        val release = CountDownLatch(1)

        val results = structured {
            val tasks = (1..6).mapVirtual(parallelism = 2) { value ->
                val current = active.incrementAndGet()
                maximumActive.accumulateAndGet(current, ::maxOf)
                try {
                    release.await(5, TimeUnit.SECONDS)
                    value * 2
                } finally {
                    active.decrementAndGet()
                }
            }

            withTimeout(5_000) {
                while (maximumActive.get() < 2) {
                    yield()
                }
            }
            release.countDown()
            tasks.awaitAll()
        }

        assertThat(results).containsExactly(2, 4, 6, 8, 10, 12)
        assertThat(maximumActive.get()).isEqualTo(2)
    }

    @Test
    fun `rejects non-positive virtual map parallelism`() {
        assertThatThrownBy {
            structured {
                listOf(1).mapVirtual(parallelism = 0) { it }
            }
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("parallelism must be positive")
    }

    @Test
    fun `propagates child failure`() {
        assertThatThrownBy {
            structured {
                asyncVirtual { throw TestException() }
            }
        }.isInstanceOf(TestException::class.java)
    }

    @Test
    fun `interrupts blocking sibling after failure`() {
        val blockingStarted = CountDownLatch(1)
        val interrupted = CountDownLatch(1)

        assertThatThrownBy {
            structured {
                asyncVirtual {
                    try {
                        blockingStarted.countDown()
                        Thread.sleep(60_000)
                    } catch (e: InterruptedException) {
                        interrupted.countDown()
                        throw e
                    }
                }
                asyncVirtual {
                    check(blockingStarted.await(5, TimeUnit.SECONDS))
                    throw TestException()
                }
            }
        }.isInstanceOf(TestException::class.java)

        assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue()
    }

    @Test
    fun `propagates common contexts`() {
        val contextKey = ContextKey.named<String>("structured-virtual-test")
        val context = Context.current().with(contextKey, "context-value")
        val principal = TestPrincipal
        val observation = TestObservation

        val result = KoraContextTestHelper.withContexts(context, principal, observation) {
            structured {
                asyncVirtual {
                    Context.current()[contextKey] == "context-value" &&
                        Principal.current() === principal &&
                        Observation.current(TestObservation::class.java) === observation
                }.await()
            }
        }

        assertThat(result).isTrue()
    }

    @Test
    fun `supports nested structured scopes`() {
        val nestedIsVirtual = structured {
            asyncVirtual {
                structured {
                    asyncVirtual { Thread.currentThread().isVirtual }.await()
                }
            }.await()
        }

        assertThat(nestedIsVirtual).isTrue()
    }

    @Test
    fun `propagates and isolates MDC between children`() {
        val parentMdc = MDC().apply {
            put0("requestId", "request-value")
        }
        val firstChildChangedMdc = CountDownLatch(1)

        val result = KoraContextTestHelper.withMdc(parentMdc) {
            structured {
                val first = asyncVirtual {
                    MDC.put("first-child", "value")
                    firstChildChangedMdc.countDown()
                    MDC.get().values().keys
                }
                val second = asyncVirtual {
                    check(firstChildChangedMdc.await(5, TimeUnit.SECONDS))
                    MDC.get().values().keys
                }
                first.await() to second.await()
            }
        }

        assertThat(result.first).contains("requestId", "first-child")
        assertThat(result.second).contains("requestId").doesNotContain("first-child")
        assertThat(parentMdc.values()).containsKey("requestId").doesNotContainKey("first-child")
    }

    private class TestException : RuntimeException()

    private data object TestPrincipal : Principal

    private data object TestObservation : Observation {
        override fun span(): Span = Span.getInvalid()

        override fun end() = Unit

        override fun observeError(e: Throwable) = Unit
    }
}
