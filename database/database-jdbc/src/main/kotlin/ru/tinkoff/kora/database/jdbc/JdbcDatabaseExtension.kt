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
    val currentConnectionCtx = this.currentConnectionContext()
    if (currentConnectionCtx != null) {
        try {
            return callback.invoke(currentConnectionCtx)
        } catch (e: SQLException) {
            throw RuntimeSqlException(e)
        }
    }

    val dispatcher = userDispatcher
        ?: executor?.asCoroutineDispatcher()
        ?: Dispatchers.IO

    val forkCtx = curCtx.fork()
    return withContext(dispatcher + Context.Kotlin.asCoroutineContext(forkCtx)) {
        try {
            val newConnection = newConnection()
            val connectionCtx = ConnectionContext(newConnection)
            setContext(forkCtx, connectionCtx)
            newConnection.use { callback.invoke(connectionCtx) }
        } catch (e: SQLException) {
            throw RuntimeSqlException(e)
        } finally {
            clearContext(forkCtx)
        }
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
            return@withConnectionCtxSuspend callback.invoke(connection)
        }
        connection.autoCommit = false
        val result: T
        try {
            result = callback.invoke(connection)
            connection.commit()
            connection.autoCommit = true
        } catch (e: Exception) {
            try {
                connection.rollback()
                connection.autoCommit = true
                connectionCtx.postRollbackActions().forEach { it.run(connection, e) }
            } catch (suppressed: Exception) {
                e.addSuppressed(suppressed)
            }
            throw e
        }
        connectionCtx.postCommitActions().forEach { it.run(connection) }
        result
    }
}
