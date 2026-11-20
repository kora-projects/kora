package io.koraframework.database.mongo;

import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseMetricsConfig_ConfigValueMapper;
import io.koraframework.test.mongo.MongoParams;
import io.koraframework.test.mongo.MongoTestContainer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MongoTestContainer.class)
class MongoDriverMetricsTest {

    private static MongoDataSource dataSource(MongoParams params, SimpleMeterRegistry registry, boolean enabled, boolean driverMetrics) {
        var metrics = new $DatabaseTelemetryConfig_DatabaseMetricsConfig_ConfigValueMapper.DatabaseMetricsConfig_Impl(driverMetrics, enabled, new Duration[0], Map.of("env", "test"));
        var config = MongoTestUtils.config(params.connectionString(), params.database(), Duration.ofSeconds(30), true, true, Duration.ofSeconds(5), metrics);
        return MongoTestUtils.createDataSource(config, registry);
    }

    @Test
    public void testDriverMetricsAreTaggedWithPoolNameAndConfiguredTags(MongoParams params) {
        var registry = new SimpleMeterRegistry();

        MongoTestUtils.withDb(dataSource(params, registry, true, true), db -> {
            db.database().getCollection("metric_items").find().first();

            var commands = registry.find("mongodb.driver.commands").meters();
            var pool = registry.find("mongodb.driver.pool.size").meters();
            assertThat(commands).isNotEmpty();
            assertThat(pool).isNotEmpty();
            assertThat(registry.find("mongodb.driver.commands").tag("collection", "metric_items").meters()).isNotEmpty();
            for (var meter : commands) {
                assertThat(meter.getId().getTag("db.client.connection.pool.name")).isEqualTo(params.database());
                assertThat(meter.getId().getTag("env")).isEqualTo("test");
                assertThat(meter.getId().getTag("cluster.id")).isNull();
            }
            for (var meter : pool) {
                assertThat(meter.getId().getTag("db.client.connection.pool.name")).isEqualTo(params.database());
                assertThat(meter.getId().getTag("cluster.id")).isNull();
            }
        });
    }

    @Test
    public void testDriverMetricsAreOffWhenTelemetryMetricsAreDisabled(MongoParams params) {
        var registry = new SimpleMeterRegistry();

        MongoTestUtils.withDb(dataSource(params, registry, false, true), db -> {
            db.database().runCommand(new Document("ping", 1));

            assertThat(registry.find("mongodb.driver.commands").meters()).isEmpty();
        });
    }

    @Test
    public void testDriverMetricsAreOffWhenDriverMetricsAreDisabled(MongoParams params) {
        var registry = new SimpleMeterRegistry();

        MongoTestUtils.withDb(dataSource(params, registry, true, false), db -> {
            db.database().runCommand(new Document("ping", 1));

            assertThat(registry.find("mongodb.driver.commands").meters()).isEmpty();
            assertThat(registry.find("mongodb.driver.pool.size").meters()).isEmpty();
        });
    }
}
