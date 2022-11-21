package ru.tinkoff.kora.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import ru.tinkoff.kora.common.util.ReactorContextHook
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

class ContextTest {

    private object TestKey : Context.KeyImmutable<String>()

    @Test
    fun reactorToCoroutineContextInjection() {
        ReactorContextHook.init()
        val startContext = Context.current()
        startContext.set(TestKey, "foo")
        val res = Mono.defer { Mono.just("bar") }
            .flatMap { s ->
                mono(Dispatchers.Default + Context.Kotlin.asCoroutineContext(Context.current())) {
                    Context.current().set(TestKey, Context.current().get(TestKey) + s)
                    delay(1)
                    Context.current().get(TestKey)
                }

            }
            .contextWrite { Context.Reactor.inject(it, startContext) }
            .block()

        Assertions.assertEquals("foobar", res)
        Assertions.assertEquals("foobar", startContext.get(TestKey))
    }

    @Test
    fun crossReactorContextTest() {
        ReactorContextHook.init()
        val startContext = Context.current()
        startContext.set(TestKey, "foo")
        val res = Mono.defer { Mono.just("bar") }
            .flatMap { s ->
                Mono.delay(Duration.ofMillis(1)).map {
                    Context.current().get(TestKey) + s
                }
            }
            .block()

        Assertions.assertEquals("foobar", res)
    }

    @Test
    fun reactorSubscribeWithCtx() {
        ReactorContextHook.init()
        val startContext = Context.current()
        startContext.set(TestKey, "foo")
        val reactorCtx = Context.Reactor.inject(reactor.util.context.Context.empty(), startContext)
        val value = AtomicReference<String>()
        val latch = CountDownLatch(1)
        Thread {
            Mono.defer { Mono.just("bar") }
                .flatMap { s ->
                    Mono.delay(Duration.ofMillis(1)).map {
                        Context.current().get(TestKey) + s
                    }
                }
                .subscribe(
                    {
                        value.set(it)
                        latch.countDown()
                    },
                    {},
                    {},
                    reactorCtx
                )
        }.start()
        latch.await()

        Assertions.assertEquals("foobar", value.get())
    }


    @Test
    fun reactorToCoroutineToReactorTest() {
        ReactorContextHook.init()
        val startContext = Context.current()
        startContext.set(TestKey, "foo")
        val res = Mono.defer { Mono.just("bar") }
            .flatMap { s ->
                mono(Dispatchers.Default + Context.Kotlin.asCoroutineContext(Context.current())) {
                    Context.current().set(TestKey, Context.current().get(TestKey) + s)
                    Mono.delay(Duration.ofMillis(1))
                        .flatMap {
                            Context.current().get(TestKey)?.let { Mono.just(it) } ?: Mono.just("nope")
                        }
                        .awaitSingle()
                        .also {
                            Assertions.assertEquals("foobar", Context.current().get(TestKey))
                        }
                }

            }
            .block()

        Assertions.assertEquals("foobar", res)
        Assertions.assertEquals("foobar", startContext.get(TestKey))
    }
}
