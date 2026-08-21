package io.koraframework.common.concurrent

import kotlinx.coroutines.*
import java.lang.Runnable
import java.lang.Thread
import java.util.concurrent.ThreadFactory
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Runs a structured group of blocking operations and returns its result synchronously.
 *
 * A failure in one child cancels its siblings. Cancellation interrupts the thread while it is
 * executing an interruptible blocking operation. Operations run on virtual threads by default
 * and may select another dispatcher with [StructuredCoroutineScope.asyncOn].
 *
 * Example using virtual threads:
 * ```kotlin
 * fun getProfile(id: Long): Profile =
 *     structured {
 *         val user = asyncVirtual {
 *             userRepository.find(id)
 *         }
 *         val orders = asyncVirtual {
 *             orderRepository.findByUser(id)
 *         }
 *
 *         Profile(
 *             user.await(),
 *             orders.await(),
 *         )
 *     }
 * ```
 *
 * A custom dispatcher may be selected for an individual operation:
 * ```kotlin
 * structured {
 *     val result = asyncOn(Dispatchers.IO) {
 *         repository.find(id)
 *     }
 *
 *     result.await()
 * }
 * ```
 */
fun <T> structured(block: suspend StructuredCoroutineScope.() -> T): T {
    val snapshot = KoraContextSnapshot.capture()
    return runBlocking {
        StructuredCoroutineScopeImpl(this, snapshot).block()
    }
}

private class StructuredCoroutineScopeImpl(
    private val scope: CoroutineScope,
    private val snapshot: KoraContextSnapshot,
) : StructuredCoroutineScope {
    override fun <T> asyncVirtual(block: () -> T): Deferred<T> {
        val childSnapshot = snapshot.fork()
        return scope.async(KoraContextElement(childSnapshot)) {
            runInterruptible(VirtualThreadDispatcher, block)
        }
    }

    override fun <T> asyncOn(dispatcher: CoroutineDispatcher, block: () -> T): Deferred<T> {
        val childSnapshot = snapshot.fork()
        return scope.async {
            runInterruptible(dispatcher) {
                childSnapshot.call(block)
            }
        }
    }

    override fun launchVirtual(block: () -> Unit): Job {
        val childSnapshot = snapshot.fork()
        return scope.launch(KoraContextElement(childSnapshot)) {
            runInterruptible(VirtualThreadDispatcher, block)
        }
    }

    override fun launchOn(dispatcher: CoroutineDispatcher, block: () -> Unit): Job {
        val childSnapshot = snapshot.fork()
        return scope.launch {
            runInterruptible(dispatcher) {
                childSnapshot.call(block)
            }
        }
    }
}

private object VirtualThreadDispatcher : CoroutineDispatcher() {
    private val threadFactory: ThreadFactory = Thread.ofVirtual()
        .name("kora-kvtd-", 0)
        .factory()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val snapshot = context[KoraContextElement]?.snapshot
        threadFactory.newThread {
            if (snapshot == null) {
                block.run()
            } else {
                snapshot.run(block)
            }
        }.start()
    }
}

private class KoraContextElement(
    val snapshot: KoraContextSnapshot,
) : AbstractCoroutineContextElement(KoraContextElement) {
    companion object : CoroutineContext.Key<KoraContextElement>
}
