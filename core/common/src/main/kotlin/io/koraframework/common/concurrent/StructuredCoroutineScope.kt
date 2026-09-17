package io.koraframework.common.concurrent

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import java.util.concurrent.Semaphore

/**
 * Scope for running blocking operations concurrently, on virtual threads by default.
 *
 * Instances are only valid for the duration of [structured].
 */
interface StructuredCoroutineScope {
    /**
     * Starts [block] on a new virtual thread.
     *
     * Cancelling the returned task interrupts the virtual thread while the block is running.
     */
    fun <T> asyncVirtual(block: () -> T): Deferred<T>

    /**
     * Starts [block] on [dispatcher] with the captured Kora context.
     *
     * Cancelling the returned task interrupts the dispatcher thread while the block is running.
     */
    fun <T> asyncOn(dispatcher: CoroutineDispatcher, block: () -> T): Deferred<T>

    /**
     * Starts [block] on a new virtual thread without producing a result.
     *
     * The task remains a child of [structured], which waits for its completion and propagates its
     * failure even when the returned job is not joined explicitly.
     */
    fun launchVirtual(block: () -> Unit): Job

    /**
     * Starts [block] on [dispatcher] without producing a result.
     *
     * The task remains a child of [structured], which waits for its completion and propagates its
     * failure even when the returned job is not joined explicitly.
     */
    fun launchOn(dispatcher: CoroutineDispatcher, block: () -> Unit): Job

    /**
     * Starts one virtual-thread task for every element in this iterable.
     *
     * Use [kotlinx.coroutines.awaitAll] to await all returned tasks.
     */
    fun <T, R> Iterable<T>.mapVirtual(block: (T) -> R): List<Deferred<R>> =
        map { value ->
            asyncVirtual {
                block(value)
            }
        }

    /**
     * Starts one virtual-thread task for every element in this iterable while allowing at most
     * [parallelism] task bodies to run concurrently.
     *
     * Use this overload when a downstream resource, such as a connection pool or remote service,
     * has a lower concurrency limit than virtual threads. [parallelism] must be positive.
     */
    fun <T, R> Iterable<T>.mapVirtual(
        parallelism: Int,
        block: (T) -> R,
    ): List<Deferred<R>> {
        require(parallelism > 0) { "parallelism must be positive" }
        val semaphore = Semaphore(parallelism)
        return mapVirtual { value ->
            semaphore.acquire()
            try {
                block(value)
            } finally {
                semaphore.release()
            }
        }
    }

    /**
     * Starts one task on [dispatcher] for every element in this iterable.
     *
     * Use [kotlinx.coroutines.awaitAll] to await all returned tasks.
     */
    fun <T, R> Iterable<T>.mapOn(
        dispatcher: CoroutineDispatcher,
        block: (T) -> R,
    ): List<Deferred<R>> =
        map { value ->
            asyncOn(dispatcher) {
                block(value)
            }
        }

}
