package ru.tinkoff.kora.database.r2dbc

import io.r2dbc.spi.ConnectionFactoryOptions
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import ru.tinkoff.kora.common.Context
import ru.tinkoff.kora.database.common.telemetry.DefaultDataBaseTelemetryFactory
import ru.tinkoff.kora.database.r2dbc.`$R2dbcDatabaseConfig_ConfigValueExtractor`.R2dbcDatabaseConfig_Impl
import ru.tinkoff.kora.telemetry.common.`$TelemetryConfig_ConfigValueExtractor`.TelemetryConfig_Impl
import ru.tinkoff.kora.telemetry.common.`$TelemetryConfig_LogConfig_ConfigValueExtractor`.LogConfig_Impl
import ru.tinkoff.kora.telemetry.common.`$TelemetryConfig_MetricsConfig_ConfigValueExtractor`.MetricsConfig_Impl
import ru.tinkoff.kora.telemetry.common.`$TelemetryConfig_TracingConfig_ConfigValueExtractor`.TracingConfig_Impl
import ru.tinkoff.kora.test.postgres.PostgresParams
import ru.tinkoff.kora.test.postgres.PostgresTestContainer
import java.time.Duration
import java.util.function.Function

@ExtendWith(PostgresTestContainer::class)
class R2dbcDatabaseExtensionTest {

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

        val sql = "SELECT * FROM $tableName WHERE value = 'test1'"

        data class Entity(val id: Long, val value: String)

        withDb(params) { db ->
            db.withConnectionSuspend {
                val connection = db.currentConnection().awaitSingle()
                val res = connection.createStatement(sql)
                    .execute()
                    .awaitSingle()

                val id = res.map { rs -> rs.get(0, java.lang.Long::class.java) }.awaitSingle()
                Assertions.assertThat(id).isEqualTo(1L)
            }
        }
    }

    @Test
    fun testTransaction(params: PostgresParams) {
        val tableName = PostgresTestContainer.randomName("test_table")
        params.execute("CREATE TABLE ${tableName}(id BIGSERIAL, value VARCHAR);")
        val sql = "INSERT INTO ${tableName}(value) VALUES ('test1');"

        withDb(params) { db ->
            try {
                db.inTxSuspend {
                    val connection = db.currentConnection().awaitSingle()
                    connection.createStatement(sql).execute().awaitSingle()
                    throw RuntimeException("boom")
                }
            } catch (e: RuntimeException) {
                if (e.message != "boom") {
                    fail("Unexpected exception", e)
                }
            }

            db.withConnectionSuspend {
                val conn = db.currentConnection().awaitSingle()
                val res = conn.createStatement("select count(*) from $tableName").execute().awaitSingle()
                val count = res.map { rs -> rs.get(0, java.lang.Long::class.java) }.awaitSingle()
                Assertions.assertThat(count).isEqualTo(0L)
            }

            db.inTxSuspend {
                val connection = db.currentConnection().awaitSingle()
                val res = connection.createStatement(sql).execute().awaitSingle()
                Assertions.assertThat(res.rowsUpdated.awaitSingle()).isEqualTo(1)
            }

            db.withConnectionSuspend {
                val conn = db.currentConnection().awaitSingle()
                val res = conn.createStatement("select count(*) from $tableName").execute().awaitSingle()
                val count = res.map { rs -> rs.get(0, java.lang.Long::class.java) }.awaitSingle()
                Assertions.assertThat(count).isEqualTo(1L)
            }
        }
    }

    companion object {
        private fun withDb(params: PostgresParams, consumer: suspend (R2dbcDatabase) -> Unit) {
            val config = R2dbcDatabaseConfig_Impl(
                "r2dbc:postgres://%s:%d/%s".formatted(params.host, params.port, params.db),
                params.user,
                params.password,
                "test",
                Duration.ofMillis(1000L),
                Duration.ofMillis(1000L),
                Duration.ofMillis(1000L),
                Duration.ofMillis(10000L),
                Duration.ofMillis(10000L),
                3,
                2,
                0,
                false,
                mapOf<String, String>(),
                TelemetryConfig_Impl(
                    LogConfig_Impl(true),
                    TracingConfig_Impl(true),
                    MetricsConfig_Impl(null, null)
                )
            )
            val db = R2dbcDatabase(
                config,
                listOf<Function<ConnectionFactoryOptions.Builder, ConnectionFactoryOptions.Builder>>(),
                DefaultDataBaseTelemetryFactory(null, null, null)
            )
            db.init()
            try {
                runBlocking(Context.Kotlin.asCoroutineContext(Context.clear())) {
                    consumer(db)
                }
            } finally {
                db.release()
                Context.clear()
            }
        }
    }
}
