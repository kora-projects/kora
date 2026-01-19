package ru.tinkoff.kora.database.jdbc

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import ru.tinkoff.kora.common.Context
import ru.tinkoff.kora.database.common.telemetry.DefaultDataBaseTelemetryFactory
import ru.tinkoff.kora.database.jdbc.`$JdbcDatabaseConfig_ConfigValueExtractor`.*
import ru.tinkoff.kora.database.jdbc.JdbcHelper.SqlFunction1
import ru.tinkoff.kora.database.jdbc.JdbcHelper.SqlRunnable
import ru.tinkoff.kora.telemetry.common.`$TelemetryConfig_ConfigValueExtractor`.*
import ru.tinkoff.kora.telemetry.common.`$TelemetryConfig_LogConfig_ConfigValueExtractor`.*
import ru.tinkoff.kora.telemetry.common.`$TelemetryConfig_MetricsConfig_ConfigValueExtractor`.*
import ru.tinkoff.kora.telemetry.common.`$TelemetryConfig_TracingConfig_ConfigValueExtractor`.*
import ru.tinkoff.kora.test.postgres.PostgresParams
import ru.tinkoff.kora.test.postgres.PostgresTestContainer
import java.sql.Connection
import java.sql.SQLException
import java.time.Duration
import java.util.*
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

@ExtendWith(PostgresTestContainer::class)
internal class SuspendJdbcDatabaseTest {

    @Test
    fun testQuery(params: PostgresParams) {
        params.setup()
        withDb(params) { db ->
            val result = db.withConnectionSuspend { c ->
                val conn = db.currentConnection()
                Assertions.assertThat(c).isSameAs(conn)
                delay(1)
                Assertions.assertThat(db.currentConnection()).isSameAs(conn)
                delay(1)
                c.selectValue()
            }
            Assertions.assertThat(result).isEqualTo(1)
        }
    }

    @Test
    fun testTransaction(params: PostgresParams) {
        params.setup()
        withDb(params) { db ->
            catchDummy {
                db.inTxSuspend { c ->
                    c.updateValue(2)
                    throw DummyException()
                }
            }
            val result = db.inTx(SqlFunction1 { c ->
                c.selectValue()
            })
            Assertions.assertThat(result).isEqualTo(1)
        }
    }

    @Test
    fun testTransactionsCombination(params: PostgresParams) {
        params.setup()

        var value = 1
        fun Connection.update(move: Boolean, delta: Int = 1) {
            updateValue(value + delta)
            if (move) {
                value += delta
            }
        }
        fun Connection.check() = Assertions.assertThat(selectValue()).isEqualTo(value)
        fun Connection.checkForUpdate() = Assertions.assertThat(selectValueForUpdate()).isEqualTo(value)

        withDb(params, 2) { db ->
            var invocations = 0
            db.inTxSuspend { c1 ->
                c1.check()
                invocations++
                db.inTxSuspend { c2 ->
                    invocations++
                    Assertions.assertThat(c1).isSameAs(c2)
                }
                invocations++
            }
            Assertions.assertThat(invocations).isEqualTo(3)

            val scope = CoroutineScope(Executors.newSingleThreadExecutor().asCoroutineDispatcher() + SupervisorJob())
            catchDummy {
                db.inTxSuspend { c ->
                    c.update(false)
                    scope.async {
                        db.withConnectionSuspend { c2 -> c2.check() }
                    }.await()
                    throw DummyException()
                }
            }
            catchDummy {
                db.inTxSuspend { c ->
                    c.checkForUpdate()
                    c.update(false)
                    db.inTxSuspend {
                        c.update(false, 2)
                    }
                    scope.async {
                        db.withConnectionSuspend { c2 -> c2.check() }
                    }.await()
                    throw DummyException()
                }
            }
            catchDummy {
                db.inTxSuspend { c1 ->
                    c1.checkForUpdate()
                    c1.update(false)
                    loseCoroutineContext {
                        db.inTx(SqlFunction1 { c2 ->
                            Assertions.assertThat(c1).isSameAs(c2)
                            c2.update(false, 2)
                        })
                    }
                    scope.async {
                        db.withConnectionSuspend { c2 -> c2.check() }
                    }.await()
                    throw DummyException()
                }
            }
            catchDummy {
                db.inTxSuspend { c1 ->
                    c1.checkForUpdate()
                    c1.update(false)
                    loseCoroutineContext {
                        runBlocking {
                            db.inTxSuspend { c2 ->
                                Assertions.assertThat(c1).isSameAs(c2)
                                c2.update(false, 2)
                            }
                        }
                    }
                    scope.async {
                        db.withConnectionSuspend { c2 -> c2.check() }
                    }.await()
                    throw DummyException()
                }
            }
            catchDummy {
                db.inTxSuspend { c ->
                    c.checkForUpdate()
                    c.update(false)
                    db.inTxSuspend {
                        throw DummyException()
                    }
                }
            }
            db.inTxSuspend { c ->
                c.checkForUpdate()
                db.inTxSuspend {
                    c.update(true)
                }
            }
            db.inTxSuspend { c ->
                c.update(true)
                db.inTxSuspend {
                    c.update(true)
                }
            }
            db.withConnectionSuspend { c ->
                c.check()
            }
            scope.cancel()
        }
    }

    @Test
    fun testConnectionLeak(params: PostgresParams) {
        params.setup()

        val dispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        val scope = CoroutineScope(dispatcher + SupervisorJob())

        withDb(params) { db ->
            data class State(
                val thread: Long = Thread.currentThread().id,
                val ctx: Context = Context.current(),
                val connection: Connection? = db.currentConnection()
            )
            lateinit var start: State
            lateinit var txSuspendStart: State
            lateinit var txSuspendEnd: State
            lateinit var firstThreadLock: State
            lateinit var afterTxSuspend: State
            lateinit var txStart: State
            lateinit var thirdParty: State
            lateinit var txEnd: State

            scope.async {
                start = State()
                db.inTxSuspend(CurrentThreadDispatcher) {
                    txSuspendStart = State()
                    scope.async {}.await() // switch to 2nd thread
                    txSuspendEnd = State()
                    scope.launch {
                        firstThreadLock = State()
                        delay(100)
                    }
                }
                afterTxSuspend = State()
                db.inTx(SqlRunnable {
                    txStart = State()
                    scope.launch {
                        thirdParty = State()
                    }
                    Thread.sleep(1000)
                    txEnd = State()
                })
            }.await()

            Assertions.assertThat(start.connection).isNull()

            Assertions.assertThat(txSuspendStart.thread).isEqualTo(start.thread)
            Assertions.assertThat(txSuspendStart.ctx).isNotSameAs(start.ctx)
            Assertions.assertThat(txSuspendStart.connection).isNotNull

            Assertions.assertThat(txSuspendEnd.thread).isNotEqualTo(txSuspendStart.thread)
            Assertions.assertThat(txSuspendEnd.ctx).isSameAs(txSuspendStart.ctx)
            Assertions.assertThat(txSuspendEnd.connection).isSameAs(txSuspendStart.connection)

            Assertions.assertThat(firstThreadLock.thread).isEqualTo(txSuspendStart.thread)
            Assertions.assertThat(firstThreadLock.ctx).isNotSameAs(txSuspendEnd.ctx)
            Assertions.assertThat(firstThreadLock.connection).isNull()

            Assertions.assertThat(afterTxSuspend.thread).isEqualTo(txSuspendEnd.thread)
            Assertions.assertThat(afterTxSuspend.ctx).isNotSameAs(txSuspendEnd.ctx)
            Assertions.assertThat(afterTxSuspend.ctx).isNotSameAs(start.ctx)
            Assertions.assertThat(afterTxSuspend.connection).isNull()

            Assertions.assertThat(txStart.thread).isEqualTo(afterTxSuspend.thread)
            Assertions.assertThat(txStart.ctx).isSameAs(afterTxSuspend.ctx)
            Assertions.assertThat(txStart.connection).isNotNull

            Assertions.assertThat(thirdParty.thread).isEqualTo(firstThreadLock.thread)
            Assertions.assertThat(thirdParty.ctx).isNotSameAs(txStart.ctx)
            Assertions.assertThat(thirdParty.connection).isNull()

            Assertions.assertThat(txEnd.thread).isEqualTo(txStart.thread)
            Assertions.assertThat(txEnd.ctx).isSameAs(txStart.ctx)
            Assertions.assertThat(txEnd.connection).isSameAs(txStart.connection)
        }
        scope.cancel()
        dispatcher.cancel()
    }

    companion object {

        private const val TABLE_NAME = "test_table";

        init {
            (LoggerFactory.getLogger("ROOT") as? Logger)?.let {
                it.setLevel(Level.INFO)
            }
            (LoggerFactory.getLogger("ru.tinkoff.kora") as? Logger)?.let {
                it.setLevel(Level.DEBUG)
            }
        }

        @Throws(SQLException::class)
        private fun withDb(params: PostgresParams, maxPoolSize: Int = 1, consumer: suspend (JdbcDatabase) -> Unit) {
            val config = JdbcDatabaseConfig_Impl(
                params.user,
                params.password,
                params.jdbcUrl(),
                "testPool",
                null,
                Duration.ofMillis(1000L),
                Duration.ofMillis(1000L),
                Duration.ofMillis(1000L),
                Duration.ofMillis(1000L),
                Duration.ofMillis(1000L),
                maxPoolSize,
                0,
                Duration.ofMillis(1000L),
                false,
                Properties(),
                TelemetryConfig_Impl(
                    LogConfig_Impl(true),
                    TracingConfig_Impl(true),
                    MetricsConfig_Impl(null, null)
                )
            )
            val db = JdbcDatabase(config, DefaultDataBaseTelemetryFactory(null, null, null), Executors.newSingleThreadExecutor())
            db.init()
            try {
                runBlocking(Context.Kotlin.asCoroutineContext(Context.current())) {
                    consumer(db)
                }
            } finally {
                db.release()
            }
        }

        private fun PostgresParams.setup() = execute(
            """
                CREATE TABLE $TABLE_NAME(id SERIAL PRIMARY KEY, value INT);
                INSERT INTO $TABLE_NAME VALUES (1, 1);
            """.trimIndent()
        )

        private fun Connection.updateValue(value: Int) = prepareStatement("UPDATE $TABLE_NAME SET value = ? WHERE id = ?;").use { stmt ->
            stmt.setInt(1, value)
            stmt.setInt(2, 1)
            stmt.execute()
        }

        private fun Connection.selectValue() = prepareStatement("SELECT value FROM $TABLE_NAME WHERE id = ?;").use { stmt ->
            stmt.setInt(1, 1)
            val resultSet = stmt.executeQuery()
            Assertions.assertThat(resultSet.next()).isTrue
            resultSet.getInt("value");
        }

        private fun Connection.selectValueForUpdate() = prepareStatement("SELECT value FROM $TABLE_NAME WHERE id = ? FOR UPDATE;").use { stmt ->
            stmt.setInt(1, 1)
            val resultSet = stmt.executeQuery()
            Assertions.assertThat(resultSet.next()).isTrue
            resultSet.getInt("value");
        }

        private suspend fun catchDummy(block: suspend () -> Unit) {
            try {
                block()
                Assertions.fail("Expected dummy exception to be thrown")
            } catch (_: DummyException) {}
        }

        private fun loseCoroutineContext(block: () -> Unit) {
            block()
        }
    }

    private class DummyException : RuntimeException()

    private object CurrentThreadDispatcher : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
    }
}
