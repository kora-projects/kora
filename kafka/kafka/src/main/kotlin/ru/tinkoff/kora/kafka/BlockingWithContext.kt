package ru.tinkoff.kora.kafka

import kotlinx.coroutines.runBlocking
import ru.tinkoff.kora.common.Context
import kotlin.coroutines.EmptyCoroutineContext

fun <T> runBlockingWithContext(block: suspend () -> T): T = runBlocking<T>(Context.Kotlin.inject(EmptyCoroutineContext, Context.current())) {
    return@runBlocking block()
}
