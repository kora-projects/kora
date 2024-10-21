package ru.tinkoff.kora.database.jdbc

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import ru.tinkoff.kora.common.Context
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.Executor
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

@Internal
@PublishedApi
internal object JdbcDatabaseExtension {

    fun getConnectionKey(jdbcDatabase: JdbcDatabase): Context.Key<Connection>? = jdbcDatabase.connectionKey

    fun setConnectionKey(jdbcDatabase: JdbcDatabase, context: Context, connection: Connection): Connection = context.set(jdbcDatabase.connectionKey, connection)

    fun getExecutor(jdbcDatabase: JdbcDatabase): Executor? = jdbcDatabase.executor
}

@Internal
data class CoroutineConnection(
    val connection: Connection
) : AbstractCoroutineContextElement(CoroutineConnection) {

    companion object Key : CoroutineContext.Key<CoroutineConnection>

    override fun toString(): String = "CoroutineConnection($connection)"
}

suspend fun <T> JdbcConnectionFactory.withConnectionSuspend(dispatcher: CoroutineDispatcher? = null, callback: suspend (Connection) -> T): T {
    val callDispatcher = dispatcher ?: if (this is JdbcDatabase)
        JdbcDatabaseExtension.getExecutor(this)?.asCoroutineDispatcher() ?: Dispatchers.IO
    else
        Dispatchers.IO

    return withContext(callDispatcher) {
        val currentConnection = this.coroutineContext[CoroutineConnection]?.connection
        if (currentConnection != null) {
            try {
                callback.invoke(currentConnection)
            } catch (e: SQLException) {
                throw RuntimeSqlException(e)
            }
        }

        try {
            newConnection().use {
                withContext(callDispatcher + CoroutineConnection(it)) {
                    callback.invoke(it)
                }
            }
        } catch (e: SQLException) {
            throw RuntimeSqlException(e)
        }
    }
}

suspend fun <T> JdbcConnectionFactory.withConnectionSuspendContext(userDispatcher: CoroutineDispatcher? = null, callback: suspend (Connection) -> T): T {
    if (this !is JdbcDatabase) {
        throw UnsupportedOperationException("Only JdbcDatabase is supported")
    }

    val factory = this
    val dispatcher = userDispatcher
        ?: JdbcDatabaseExtension.getExecutor(factory)?.asCoroutineDispatcher()
        ?: Dispatchers.IO

    val koraCurrent = Context.current()
    val koraCrtContext = Context.Kotlin.asCoroutineContext(koraCurrent)
    return withContext(dispatcher + koraCrtContext) {
        val currentConnection = koraCurrent[JdbcDatabaseExtension.getConnectionKey(factory)]
        if (currentConnection != null) {
            try {
                callback.invoke(currentConnection)
            } catch (e: SQLException) {
                throw RuntimeSqlException(e)
            }
        }

        try {
            val koraFork = koraCurrent.fork()
            koraFork.inject()
            val newConnection = newConnection()
            JdbcDatabaseExtension.setConnectionKey(factory, koraFork, newConnection)
            newConnection.use {
                withContext(dispatcher + koraCrtContext + CoroutineConnection(it)) {
                    callback.invoke(it)
                }
            }
        } catch (e: SQLException) {
            throw RuntimeSqlException(e)
        }
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

suspend fun <T> JdbcConnectionFactory.inTxSuspendContext(context: CoroutineDispatcher? = null, callback: suspend (Connection) -> T): T {
    return this.withConnectionSuspendContext(context) { connection ->
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
