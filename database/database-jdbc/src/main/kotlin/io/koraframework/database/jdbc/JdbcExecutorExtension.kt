package io.koraframework.database.jdbc

import java.sql.Connection

/**
 * <b>Русский</b>: Выполняет операцию на JDBC-соединении в ручном режиме. Kotlin-обёртка над
 * перегруженными методами [JdbcExecutor.withConnection], которая снимает неоднозначность
 * вывода перегрузок (overload resolution ambiguity) при передаче лямбды из Kotlin.
 * <hr>
 * <b>English</b>: Runs an operation on a JDBC connection in manual mode. A Kotlin wrapper over the
 * overloaded [JdbcExecutor.withConnection] methods that removes the overload-resolution ambiguity
 * when a lambda is passed from Kotlin.
 */
inline fun <T> JdbcExecutor.withConnectionKt(crossinline callback: (Connection) -> T): T =
    withConnection(JdbcExecutor.SqlFunction<Connection, T> { callback(it) })

/**
 * <b>Русский</b>: Выполняет операцию на JDBC-соединении в рамках транзакции. Kotlin-обёртка над
 * перегруженными методами [JdbcExecutor.inTx], которая снимает неоднозначность вывода
 * перегрузок (overload resolution ambiguity) при передаче лямбды из Kotlin.
 * <hr>
 * <b>English</b>: Runs an operation on a JDBC connection within a transaction. A Kotlin wrapper over
 * the overloaded [JdbcExecutor.inTx] methods that removes the overload-resolution ambiguity
 * when a lambda is passed from Kotlin.
 */
inline fun <T> JdbcExecutor.inTxKt(crossinline callback: (ConnectionContext) -> T): T =
    inTx(JdbcExecutor.SqlFunction<ConnectionContext, T> { callback(it) })
