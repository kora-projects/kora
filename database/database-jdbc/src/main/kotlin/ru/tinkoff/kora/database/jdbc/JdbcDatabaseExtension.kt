package ru.tinkoff.kora.database.jdbc

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.tinkoff.kora.common.Context
import java.sql.Connection
import java.sql.SQLException

suspend fun <T> JdbcConnectionFactory.withConnectionSuspend(userDispatcher: CoroutineDispatcher? = null, callback: suspend (Connection) -> T): T {
    if (this !is JdbcDatabase) {
        throw UnsupportedOperationException("Only JdbcDatabase is supported")
    }

    val factory = this

    val curCtx = Context.current()
    val currentConnection = curCtx[factory.connectionKey]
    if (currentConnection != null) {
        try {
            return callback.invoke(currentConnection)
        } catch (e: SQLException) {
            throw RuntimeSqlException(e)
        }
    }

    try {
        val dispatcher = userDispatcher
            ?: factory.executor?.asCoroutineDispatcher()
            ?: Dispatchers.IO

        val forkCtx = curCtx.fork()
        return withContext(dispatcher + Context.Kotlin.asCoroutineContext(forkCtx)) {
            try {
                val newConnection = newConnection()
                forkCtx[factory.connectionKey] = newConnection
                newConnection.use {
                    callback.invoke(it)
                }
            } catch (e: SQLException) {
                throw RuntimeSqlException(e)
            } finally {
                forkCtx.remove(factory.connectionKey)
            }
        }
    } finally {
        curCtx.inject()
    }
}

suspend fun <T> JdbcConnectionFactory.inTxSuspend(context: CoroutineDispatcher? = null, callback: suspend (Connection) -> T): T {
    return this.withConnectionSuspend(context) { connection ->
        if (!connection.autoCommit) {
            callback.invoke(connection)
        }

        connection.autoCommit = false
        try {
            val result: T = callback.invoke(connection)
            connection.commit()
            connection.autoCommit = true
            result
        } catch (e: Exception) {
            try {
                connection.rollback()
                connection.autoCommit = true
            } catch (sqlException: SQLException) {
                e.addSuppressed(sqlException)
            }
            throw e
        }
    }
}
