package ru.tinkoff.kora.database.r2dbc

import io.r2dbc.spi.Connection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import reactor.core.publisher.Mono
import ru.tinkoff.kora.common.Context
import kotlin.coroutines.coroutineContext

suspend inline fun <T> R2dbcConnectionFactory.withConnectionSuspend(noinline callback: suspend (Connection) -> T): T {
    val ctx = coroutineContext
    val future = withConnection {
        val current = Context.current()
        Mono.fromFuture(CoroutineScope(ctx).future {
            current.inject()
            val res = callback.invoke(it)
            Context.clear()
            res
        })
    }
    return future.toFuture().await()
}

suspend inline fun <T> R2dbcConnectionFactory.inTxSuspend(noinline callback: suspend (Connection) -> T): T {
    val ctx = coroutineContext
    val future = inTx {
        val current = Context.current()
        Mono.fromFuture(CoroutineScope(ctx).future(ctx) {
            current.inject()
            val res = callback.invoke(it)
            Context.clear()
            res
        })
    }
    return future.toFuture().await()
}
