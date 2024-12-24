package ru.tinkoff.kora.database.r2dbc

import io.r2dbc.spi.Connection
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.reactor.mono
import ru.tinkoff.kora.common.Context

@Suppress("UNCHECKED_CAST")
suspend fun <T> R2dbcConnectionFactory.withConnectionSuspend(callback: suspend (Connection) -> T): T {
    val factory = this as R2dbcDatabase

    val curCtx = Context.current()
    val currentConnection = curCtx[factory.connectionKey]
    val currentTx = curCtx[factory.transactionKey]
    val fork = curCtx.fork()
    if(currentConnection != null) {
        fork[factory.connectionKey] = currentConnection
    }
    if(currentTx != null) {
        fork[factory.transactionKey] = currentTx
    }

    val mono = withConnection {
        mono(Context.Kotlin.asCoroutineContext(fork)) {
            callback.invoke(it)
        }
    }
    return mono.awaitSingleOrNull() as T
}

@Suppress("UNCHECKED_CAST")
suspend fun <T> R2dbcConnectionFactory.inTxSuspend(callback: suspend (Connection) -> T): T {
    val factory = this as R2dbcDatabase

    val curCtx = Context.current()
    val currentConnection = curCtx[factory.connectionKey]
    val currentTx = curCtx[factory.transactionKey]
    val fork = curCtx.fork()
    if(currentConnection != null) {
        fork[factory.connectionKey] = currentConnection
    }
    if(currentTx != null) {
        fork[factory.transactionKey] = currentTx
    }

    val mono = inTx {
        mono(Context.Kotlin.asCoroutineContext(fork)) {
            callback.invoke(it)
        }
    }
    return mono.awaitSingleOrNull() as T
}
