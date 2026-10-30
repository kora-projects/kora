package ru.tinkoff.kora.database.common.annotation.processor.jdbc;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.factory.MapConfigFactory;
import ru.tinkoff.kora.database.annotation.processor.RepositoryAnnotationProcessor;
import ru.tinkoff.kora.database.jdbc.JdbcDatabase;
import ru.tinkoff.kora.database.jdbc.JdbcRepository;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JdbcRepositoryRefreshTest extends AbstractAnnotationProcessorTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @SuppressWarnings("unchecked")
    void testHikariPoolRefreshThroughRepositoryConsumer(boolean valueOfUnchangedConfig) throws Exception {
        var driver = mock(Driver.class);
        when(driver.acceptsURL("jdbc:kora:refresh")).thenReturn(true);
        when(driver.connect(eq("jdbc:kora:refresh"), any())).thenAnswer(invocation -> {
            var connection = mock(Connection.class);
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.isValid(anyInt())).thenReturn(true);
            when(connection.prepareStatement(anyString())).thenAnswer(call -> mock(PreparedStatement.class));
            return connection;
        });
        DriverManager.registerDriver(driver);
        try {
            compile(List.of(new KoraAppProcessor(), new RepositoryAnnotationProcessor()), """
                import ru.tinkoff.kora.database.jdbc.*;
                import ru.tinkoff.kora.config.common.*;
                import ru.tinkoff.kora.config.common.extractor.*;
                import java.util.concurrent.atomic.AtomicReference;
                import java.util.function.Supplier;
                
                @KoraApp
                public interface Application extends JdbcDatabaseModule {
                    AtomicReference<Config> CONFIG = new AtomicReference<>();
                
                    default Config config() { return CONFIG.get(); }
                
                    default String serviceConfig(Config config) { return "unchanged"; }
                
                    default Consumer consumer(%sTestRepository repository) { return new Consumer(repository); }
                
                    default ConfigValueExtractor<JdbcDatabaseConfig> extractor() {
                        var telemetry = org.mockito.Mockito.mock(ru.tinkoff.kora.telemetry.common.TelemetryConfig.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
                        return new $JdbcDatabaseConfig_ConfigValueExtractor(
                            new DurationConfigValueExtractor(), new PropertiesConfigValueExtractor(),
                            value -> telemetry);
                    }
                
                    @Root
                    default Supplier<Consumer> caller(ru.tinkoff.kora.application.graph.ValueOf<Consumer> consumer) {
                        return consumer::get;
                    }
                }
                """.formatted(valueOfUnchangedConfig ? "ru.tinkoff.kora.application.graph.ValueOf<String> settings, " : ""), """
                @ru.tinkoff.kora.database.common.annotation.Repository
                public interface TestRepository extends ru.tinkoff.kora.database.jdbc.JdbcRepository {
                    @ru.tinkoff.kora.database.common.annotation.Query("SELECT 1")
                    void query();
                }
                """, """
                public record Consumer(TestRepository repository) {
                    public void query() { repository.query(); }
                }
                """);
            compileResult.assertSuccess();
            var config = (AtomicReference<Config>) loadClass("Application").getField("CONFIG").get(null);
            config.set(poolConfig(0));
            var draw = loadGraphDraw("Application");
            var graph = draw.init();
            try {
                var configNode = draw.findNodeByType(Config.class);
                var databaseNode = draw.findNodeByType(JdbcDatabase.class);
                var consumerNode = draw.findNodeByType(loadClass("Consumer"));
                var callerNode = draw.getNodes().stream().filter(n -> graph.get(n) instanceof Supplier<?>).findFirst().orElseThrow();
                var caller = (Supplier<?>) graph.get(callerNode);
                var database = (JdbcDatabase) graph.get(databaseNode);
                var consumer = graph.get(consumerNode);
                new TestObject(consumer.getClass(), consumer).invoke("query");
                for (int minIdle = 1; minIdle <= 3; minIdle++) {
                    var oldPool = (HikariDataSource) database.value();
                    var oldConsumer = consumer;
                    config.set(poolConfig(minIdle));

                    graph.valueOf(configNode).refresh();

                    database = (JdbcDatabase) graph.get(databaseNode);
                    consumer = caller.get();
                    assertThat(oldPool.isClosed()).isTrue();
                    assertThat(((HikariDataSource) database.value()).isClosed()).isFalse();
                    new TestObject(consumer.getClass(), consumer).invoke("query");
                    assertThat(graph.get(callerNode)).isSameAs(caller);
                    assertThat(consumer).isNotSameAs(oldConsumer).isSameAs(graph.get(consumerNode));
                    var repository = (JdbcRepository) consumer.getClass().getMethod("repository").invoke(consumer);
                    assertThat(repository.getJdbcConnectionFactory()).isSameAs(database);
                    assertThat(((HikariDataSource) database.value()).getMinimumIdle()).isEqualTo(minIdle);
                }
                var currentDatabase = database;
                config.set(poolConfig(3));
                graph.valueOf(configNode).refresh();
                assertThat(graph.get(databaseNode)).isSameAs(currentDatabase);
                assertThat(((HikariDataSource) database.value()).isClosed()).isFalse();
                new TestObject(caller.get().getClass(), caller.get()).invoke("query");
            } finally {
                graph.release();
            }
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    private static Config poolConfig(int minIdle) {
        return MapConfigFactory.fromMap(Map.of("db", Map.of(
            "jdbcUrl", "jdbc:kora:refresh", "username", "test", "password", "test", "poolName", "refresh-test", "minIdle", minIdle)));
    }
}
