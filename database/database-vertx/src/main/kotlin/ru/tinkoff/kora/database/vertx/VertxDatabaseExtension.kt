package ru.tinkoff.kora.database.vertx

import io.vertx.sqlclient.SqlClient
import io.vertx.sqlclient.SqlConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

suspend fun <T> VertxConnectionFactory.withConnectionSuspend(callback: suspend (SqlClient) -> T): T {
    return coroutineScope {
        val future = withConnection {
            future {
                callback.invoke(it)
            }
        }
        future.await()
    }
}

suspend fun <T> VertxConnectionFactory.inTxSuspend(callback: suspend (SqlConnection) -> T): T {
    return coroutineScope {
        val future = inTx {
            future<T> {
                callback.invoke(it)
            }
        }
        future.await()
    }
}
