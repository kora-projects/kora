package ru.tinkoff.kora.database.jdbc

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import ru.tinkoff.kora.common.Context
import ru.tinkoff.kora.database.common.telemetry.DefaultDataBaseTelemetryFactory
import ru.tinkoff.kora.database.jdbc.`$JdbcDatabaseConfig_ConfigValueExtractor`.*
import ru.tinkoff.kora.database.jdbc.JdbcHelper.SqlRunnable
import ru.tinkoff.kora.telemetry.common.`$TelemetryConfig_ConfigValueExtractor`.*
import ru.tinkoff.kora.telemetry.common.`$TelemetryConfig_LogConfig_ConfigValueExtractor`.*
import ru.tinkoff.kora.telemetry.common.`$TelemetryConfig_MetricsConfig_ConfigValueExtractor`.*
import ru.tinkoff.kora.telemetry.common.`$TelemetryConfig_TracingConfig_ConfigValueExtractor`.*
import ru.tinkoff.kora.test.postgres.PostgresParams
import ru.tinkoff.kora.test.postgres.PostgresParams.ResultSetMapper
import ru.tinkoff.kora.test.postgres.PostgresTestContainer
import java.sql.ResultSet
import java.sql.SQLException
import java.time.Duration
import java.util.*
import java.util.concurrent.Executors

@ExtendWith(PostgresTestContainer::class)
internal class SuspendJdbcDatabaseTest {

    @Test
    fun testQuery(params: PostgresParams) {
        val tableName = PostgresTestContainer.randomName("test_table")
        params.execute(
            """
            CREATE TABLE ${tableName}(id BIGSERIAL, value VARCHAR);
            INSERT INTO ${tableName}(value) VALUES ('test1');
            INSERT INTO ${tableName}(value) VALUES ('test2');
            """
        )

        val id = "SELECT * FROM $tableName WHERE value = :value"
        val sql = "SELECT * FROM $tableName WHERE value = ?"

        data class Entity(val id: Long, val value: String)


        Context.clear()
        Context.current().set(object : Context.Key<String>() {
            override fun copy(it: String): String {
                return it
            }
        }, "value")
        withDb(params) { db: JdbcDatabase ->
            val result = db.withConnectionSuspend {
                val conn = db.currentConnection()
                delay(1)
                Assertions.assertThat(db.currentConnection()).isSameAs(conn)
                delay(1)

                val r = ArrayList<Entity>()
                db.currentConnection().prepareStatement(sql).use { stmt ->
                    stmt.setString(1, "test1")
                    val rs: ResultSet = stmt.executeQuery()
                    while (rs.next()) {
                        r.add(Entity(rs.getInt(1).toLong(), rs.getString(2)))
                    }
                }
                r
            }

            Assertions.assertThat(result).containsExactly(Entity(1, "test1"))
        }
    }

    @Test
    fun testTransaction(params: PostgresParams) {
        val tableName = "test_table_" + PostgresTestContainer.randomName("test_table")
        params.execute("CREATE TABLE %s(id BIGSERIAL, value VARCHAR);".formatted(tableName))
        val id = "INSERT INTO %s(value) VALUES ('test1');".formatted(tableName)
        val sql = "INSERT INTO %s(value) VALUES ('test1');".formatted(tableName)
        val extractor =
            ResultSetMapper<List<String>, RuntimeException> { rs: ResultSet ->
                val result = ArrayList<String>()
                try {
                    while (rs.next()) {
                        result.add(rs.getString(1))
                    }
                } catch (sqlException: SQLException) {
                    throw RuntimeException(sqlException)
                }
                result
            }

        withDb(params) { db: JdbcDatabase ->
            try {
                db.inTxSuspend {
                    db.currentConnection().prepareStatement(sql).use { stmt ->
                        stmt.execute()
                    }
                    throw RuntimeException("bah")
                }
            } catch (e: Exception) {
                if (e.message != "bah") {
                    throw RuntimeException("something wrong", e)
                }
            }

            var values = params.query(
                "SELECT value FROM %s".formatted(tableName), extractor
            )
            Assertions.assertThat(values).hasSize(0)

            db.inTx(SqlRunnable {
                db.currentConnection().prepareStatement(sql).use { stmt ->
                    stmt.execute()
                }
            })

            values = params.query(
                "SELECT value FROM %s".formatted(tableName), extractor
            )
            Assertions.assertThat(values).hasSize(1)
        }
    }

    companion object {
        init {
            (LoggerFactory.getLogger("ROOT") as? Logger)?.let {
                it.setLevel(Level.INFO)
            }
            (LoggerFactory.getLogger("ru.tinkoff.kora") as? Logger)?.let {
                it.setLevel(Level.DEBUG)
            }
        }

        @Throws(SQLException::class)
        private fun withDb(params: PostgresParams, consumer: suspend (JdbcDatabase) -> Unit) {
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
                1,
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
    }
}
