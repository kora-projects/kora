package ru.tinkoff.kora.database.symbol.processor.jdbc

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw
import ru.tinkoff.kora.config.common.Config
import ru.tinkoff.kora.config.common.factory.MapConfigFactory
import ru.tinkoff.kora.database.jdbc.JdbcDatabase
import ru.tinkoff.kora.database.jdbc.JdbcRepository
import ru.tinkoff.kora.database.symbol.processor.RepositorySymbolProcessorProvider
import ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider
import java.sql.Connection
import java.sql.Driver
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

class JdbcRepositoryRefreshTest : AbstractJdbcRepositoryTest() {

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    @Suppress("UNCHECKED_CAST")
    fun testHikariPoolRefreshThroughRepositoryConsumer(valueOfUnchangedConfig: Boolean) {
        val driver = mock<Driver>()
        whenever(driver.acceptsURL("jdbc:kora:refresh")).thenReturn(true)
        whenever(driver.connect(eq("jdbc:kora:refresh"), any())).thenAnswer {
            val connection = mock<Connection>()
            whenever(connection.autoCommit).thenReturn(true)
            whenever(connection.isValid(any())).thenReturn(true)
            whenever(connection.prepareStatement(any<String>())).thenAnswer { mock<PreparedStatement>() }
            connection
        }
        DriverManager.registerDriver(driver)
        try {
            compile0(
                listOf(KoraAppProcessorProvider(), RepositorySymbolProcessorProvider()), """
                import org.mockito.kotlin.*
                import ru.tinkoff.kora.config.common.*
                import ru.tinkoff.kora.config.common.extractor.*
                import java.util.concurrent.atomic.AtomicReference
                import java.util.function.Supplier

                @KoraApp
                interface Application : JdbcDatabaseModule {
                    companion object {
                        @JvmField val CONFIG = AtomicReference<Config>()
                    }

                    fun config(): Config = CONFIG.get()

                    fun serviceConfig(config: Config): String = "unchanged"

                    fun consumer(${if (valueOfUnchangedConfig) "settings: ru.tinkoff.kora.application.graph.ValueOf<String>, " else ""}repository: TestRepository) = Consumer(repository)

                    fun extractor(): ConfigValueExtractor<JdbcDatabaseConfig> {
                        val telemetry = mock<ru.tinkoff.kora.telemetry.common.TelemetryConfig>(defaultAnswer = org.mockito.Mockito.RETURNS_DEEP_STUBS)
                        return `${'$'}JdbcDatabaseConfig_ConfigValueExtractor`(
                            DurationConfigValueExtractor(), PropertiesConfigValueExtractor(),
                            ConfigValueExtractor { telemetry })
                    }

                    @Root
                    fun caller(consumer: ru.tinkoff.kora.application.graph.ValueOf<Consumer>): Supplier<Consumer> = Supplier { consumer.get() }
                }
                """.trimIndent(), """
                @Repository
                interface TestRepository : JdbcRepository {
                    @Query("SELECT 1")
                    fun query()
                }
                """.trimIndent(), """
                data class Consumer(val repository: TestRepository) {
                    fun query() { repository.query() }
                }
                """.trimIndent()
            )
            compileResult.assertSuccess()
            val config = loadClass("Application").getField("CONFIG").get(null) as AtomicReference<Config>
            config.set(poolConfig(0))
            val supplier = loadClass("ApplicationGraph").constructors[0].newInstance() as Supplier<*>
            val draw = supplier.get() as ApplicationGraphDraw
            val graph = draw.init()
            try {
                val configNode = draw.findNodeByType(Config::class.java)!!
                val databaseNode = draw.findNodeByType(JdbcDatabase::class.java)!!
                val consumerNode = draw.findNodeByType(loadClass("Consumer"))!!
                val callerNode = draw.nodes.first { graph.get(it) is Supplier<*> }
                val caller = graph.get(callerNode) as Supplier<*>
                var database = graph.get(databaseNode) as JdbcDatabase
                var consumer = graph.get(consumerNode)!!
                TestObject(consumer::class, consumer).invoke<Any>("query")
                for (minIdle in 1..3) {
                    val oldPool = database.value() as HikariDataSource
                    val oldConsumer = consumer
                    config.set(poolConfig(minIdle))

                    graph.valueOf(configNode).refresh()

                    database = graph.get(databaseNode) as JdbcDatabase
                    consumer = caller.get()!!
                    assertThat(oldPool.isClosed).isTrue()
                    assertThat((database.value() as HikariDataSource).isClosed).isFalse()
                    TestObject(consumer::class, consumer).invoke<Any>("query")
                    assertThat(graph.get(callerNode)).isSameAs(caller)
                    assertThat(consumer).isNotSameAs(oldConsumer).isSameAs(graph.get(consumerNode))
                    val repository = consumer.javaClass.getMethod("getRepository").invoke(consumer) as JdbcRepository
                    assertThat(repository.jdbcConnectionFactory).isSameAs(database)
                    assertThat((database.value() as HikariDataSource).minimumIdle).isEqualTo(minIdle)
                }
                val currentDatabase = database
                config.set(poolConfig(3))
                graph.valueOf(configNode).refresh()
                assertThat(graph.get(databaseNode)).isSameAs(currentDatabase)
                assertThat((database.value() as HikariDataSource).isClosed).isFalse()
                TestObject(consumer::class, consumer).invoke<Any>("query")
            } finally {
                graph.release()
            }
        } finally {
            DriverManager.deregisterDriver(driver)
        }
    }

    private fun poolConfig(minIdle: Int): Config = MapConfigFactory.fromMap(
        mapOf(
            "db" to mapOf(
                "jdbcUrl" to "jdbc:kora:refresh", "username" to "test", "password" to "test", "poolName" to "refresh-test", "minIdle" to minIdle
            )
        )
    )
}
