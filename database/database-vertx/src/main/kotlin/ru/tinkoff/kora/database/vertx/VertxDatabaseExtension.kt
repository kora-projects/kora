package ru.tinkoff.kora.database.vertx

import io.vertx.sqlclient.SqlClient
import io.vertx.sqlclient.SqlConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import ru.tinkoff.kora.common.Context
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

suspend inline fun <T> VertxConnectionFactory.withConnectionSuspend(context: CoroutineContext? = null, noinline callback: suspend (SqlClient) -> T): T {
    val ctx = context ?: coroutineContext
    val future = withConnection {
        val current = Context.current()
        CoroutineScope(ctx).future {
            current.inject()
            val res = callback.invoke(it)
            Context.clear()
            res
        }
    }
    return future.await()
}

suspend inline fun <T> VertxConnectionFactory.inTxSuspend(context: CoroutineContext? = null, noinline callback: suspend (SqlConnection) -> T): T {
    val ctx = context ?: coroutineContext
    val future = inTx {
        val current = Context.current()
        CoroutineScope(ctx).future<T>(ctx) {
            current.inject()
            val res = callback.invoke(it)
            Context.clear()
            res
        }
    }
    return future.await()
}
