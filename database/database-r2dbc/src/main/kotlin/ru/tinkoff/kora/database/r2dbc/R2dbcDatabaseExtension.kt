package ru.tinkoff.kora.database.r2dbc

import io.r2dbc.spi.Connection
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

suspend inline fun <T> R2dbcConnectionFactory.withConnectionSuspend(context: CoroutineContext? = null, noinline callback: suspend (Connection) -> T): T {
    val ctx = context ?: coroutineContext
    val mono = withConnection {
        mono(ctx) {
            callback.invoke(it)
        }
    }
    return mono.awaitSingle()
}

suspend inline fun <T> R2dbcConnectionFactory.inTxSuspend(context: CoroutineContext? = null, noinline callback: suspend (Connection) -> T): T {
    val ctx = context ?: coroutineContext
    val mono = inTx {
        mono(ctx) {
            callback.invoke(it)
        }
    }
    return mono.awaitSingle()
}
