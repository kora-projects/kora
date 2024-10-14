package ru.tinkoff.kora.database.jdbc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import ru.tinkoff.kora.common.Context
import java.sql.Connection
import kotlin.coroutines.coroutineContext

suspend inline fun <T> JdbcConnectionFactory.withConnectionSuspend(noinline callback: suspend (Connection) -> T): T {
    val ctx = coroutineContext
    val future = withConnectionStage {
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

suspend inline fun <T> JdbcConnectionFactory.inTxSuspend(noinline callback: suspend (Connection) -> T): T {
    val ctx = coroutineContext
    val future = inTxStage {
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
