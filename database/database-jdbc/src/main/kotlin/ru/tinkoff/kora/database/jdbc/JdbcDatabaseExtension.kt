package ru.tinkoff.kora.database.jdbc

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.tinkoff.kora.common.Context
import java.sql.Connection
import java.sql.SQLException

private suspend fun <T> JdbcConnectionFactory.withConnectionInternal(
    userDispatcher: CoroutineDispatcher? = null,
    callback: suspend (ConnectionContext) -> T
): T {
    if (this !is JdbcDatabase) {
        throw UnsupportedOperationException("Only JdbcDatabase is supported")
    }

    val curCtx = Context.current()
    val currentConnectionCtx = ConnectionContext.get(curCtx)
    if (currentConnectionCtx != null) {
        try {
            return callback.invoke(currentConnectionCtx)
        } catch (e: SQLException) {
            throw RuntimeSqlException(e)
        }
    }

    try {
        val dispatcher = userDispatcher
            ?: executor?.asCoroutineDispatcher()
            ?: Dispatchers.IO

        val forkCtx = curCtx.fork()
        return withContext(dispatcher + Context.Kotlin.asCoroutineContext(forkCtx)) {
            try {
                val newConnection = newConnection()
                val connectionCtx = ConnectionContext(newConnection)
                ConnectionContext.set(forkCtx, connectionCtx)
                newConnection.use { callback.invoke(connectionCtx) }
            } catch (e: SQLException) {
                throw RuntimeSqlException(e)
            } finally {
                ConnectionContext.remove(forkCtx)
            }
        }
    } finally {
        curCtx.inject()
    }
}

suspend fun <T> JdbcConnectionFactory.withConnectionSuspend(
    userDispatcher: CoroutineDispatcher? = null,
    callback: suspend (Connection) -> T
): T = withConnectionInternal(userDispatcher) { ctx -> callback(ctx.connection()) }

suspend fun <T> JdbcConnectionFactory.withConnectionCtxSuspend(
    userDispatcher: CoroutineDispatcher? = null,
    callback: suspend (ConnectionContext) -> T
): T = withConnectionInternal(userDispatcher, callback)

suspend fun <T> JdbcConnectionFactory.inTxSuspend(context: CoroutineDispatcher? = null, callback: suspend (Connection) -> T): T {
    return this.withConnectionCtxSuspend(context) { connectionCtx ->
        val connection = connectionCtx.connection()
        if (!connection.autoCommit) {
            callback.invoke(connection)
        }

        connection.autoCommit = false
        try {
            val result: T = callback.invoke(connection)
            connection.commit()
            connectionCtx.postCommitActions().forEach { it::run }
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
