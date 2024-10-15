package ru.tinkoff.kora.database.jdbc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.sql.Connection
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

suspend inline fun <T> JdbcConnectionFactory.withConnectionSuspend(context: CoroutineContext? = null, noinline callback: suspend (Connection) -> T): T {
    val ctx = context ?: coroutineContext
    val deferred = CoroutineScope(ctx).async {
        val result = withConnection(JdbcHelper.SqlFunction1 {
            runBlocking(ctx) {
                callback.invoke(it)
            }
        })
        result
    }
    return deferred.await()
}

suspend inline fun <T> JdbcConnectionFactory.inTxSuspend(context: CoroutineContext? = null, noinline callback: suspend (Connection) -> T): T {
    val ctx = context ?: coroutineContext
    val deferred = CoroutineScope(ctx).async {
        val result = inTx(JdbcHelper.SqlFunction1 {
            runBlocking(ctx) {
                callback.invoke(it)
            }
        })
        result
    }
    return deferred.await()
}
