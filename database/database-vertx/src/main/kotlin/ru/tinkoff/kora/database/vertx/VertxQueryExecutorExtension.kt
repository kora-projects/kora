package ru.tinkoff.kora.database.vertx

import io.vertx.sqlclient.*
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import ru.tinkoff.kora.database.common.QueryContext
import kotlin.coroutines.coroutineContext

suspend inline fun <T> VertxConnectionFactory.withConnection(noinline callback: suspend (SqlClient) -> T): T {
    val ctx = coroutineContext
    return withConnection {
        mono<T>(ctx) {
            callback.invoke(it)
        }
    }
        .awaitSingle()
}

suspend inline fun <T> VertxConnectionFactory.inTx(noinline callback: suspend (SqlConnection) -> T): T {
    val ctx = coroutineContext
    return this.inTx {
        mono<T>(ctx) {
            callback.invoke(it)
        }
    }.awaitSingle()
}
//
//suspend inline fun <T> VertxConnectionFactory.query(connection: SqlConnection, query: QueryContext, noinline parameters: () -> Tuple, noinline mapper: (RowSet<Row>) -> T): T {
//    return this.query(connection, query, parameters, mapper).awaitSingle()
//}
//
//suspend inline fun <T> VertxConnectionFactory.query(query: QueryContext, noinline parameters: () -> Tuple, noinline mapper: (RowSet<Row>) -> T): T {
//    return this.query(query, parameters, mapper).awaitSingle()
//}
