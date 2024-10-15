package ru.tinkoff.kora.database.jdbc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import kotlinx.coroutines.runBlocking
import ru.tinkoff.kora.common.Context
import java.sql.Connection
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

suspend inline fun <T> JdbcConnectionFactory.withConnectionSuspend(context: CoroutineContext? = null, noinline callback: suspend (Connection) -> T): T {
    val ctx = context ?: coroutineContext
    val current = Context.current()
    val future = CoroutineScope(ctx).future {
        val old = Context.current()

        val fork = current.fork()
        fork.inject()

        val result = withConnection(JdbcHelper.SqlFunction1 {
            runBlocking(ctx) {
                callback.invoke(it)
            }
        })

        old.inject()
        result
    }
    return future.await()
}

suspend inline fun <T> JdbcConnectionFactory.inTxSuspend(context: CoroutineContext? = null, noinline callback: suspend (Connection) -> T): T {
    val ctx = context ?: coroutineContext
    val current = Context.current()
    val future = CoroutineScope(ctx).future {
        val old = Context.current()

        val fork = current.fork()
        fork.inject()

        val result = inTx(JdbcHelper.SqlFunction1 {
            runBlocking(ctx) {
                callback.invoke(it)
            }
        })

        old.inject()
        result
    }
    return future.await()
}
