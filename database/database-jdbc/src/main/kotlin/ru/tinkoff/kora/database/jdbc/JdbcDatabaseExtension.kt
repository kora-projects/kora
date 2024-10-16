package ru.tinkoff.kora.database.jdbc

import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import ru.tinkoff.kora.common.Context
import java.sql.Connection
import java.sql.SQLException
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

@Internal
@PublishedApi
internal object JdbcDatabaseExtension {

    fun getConnectionKey(jdbcDatabase: JdbcDatabase): Context.Key<Connection>? = jdbcDatabase.connectionKey
}

@Internal
data class CoroutineConnection(
    val connection: Connection
) : AbstractCoroutineContextElement(CoroutineConnection) {

    companion object Key : CoroutineContext.Key<CoroutineConnection>

    override fun toString(): String = "CoroutineConnection($connection)"
}

suspend inline fun <T> JdbcConnectionFactory.withConnectionSuspend(context: CoroutineContext? = null, noinline callback: suspend (Connection) -> T): T {
    val crtContext = context ?: coroutineContext
    return withContext(crtContext) {
        val currentConnection = this.coroutineContext[CoroutineConnection]?.connection
        if (currentConnection != null) {
            try {
                callback.invoke(currentConnection)
            } catch (e: SQLException) {
                throw RuntimeSqlException(e)
            }
        }

        async {
            try {
                newConnection().use {
                    withContext(crtContext + CoroutineConnection(it)) {
                        callback.invoke(it)
                    }
                }
            } catch (e: SQLException) {
                throw RuntimeSqlException(e)
            }
        }.await()
    }
}

suspend inline fun <T> JdbcConnectionFactory.inTxSuspend(context: CoroutineContext? = null, noinline callback: suspend (Connection) -> T): T {
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
