package io.koraframework.database.mongo;

import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_ConfigValueMapper;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseLoggingConfig_ConfigValueMapper;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseMetricsConfig_ConfigValueMapper;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseTracingConfig_ConfigValueMapper;
import io.koraframework.database.common.telemetry.impl.DefaultDatabaseTelemetryFactory;
import io.koraframework.database.common.telemetry.impl.NoopDatabaseLoggerFactory;
import io.koraframework.database.common.telemetry.impl.NoopDatabaseMetricsFactory;
import io.koraframework.micrometer.common.NoopMeterRegistry;
import io.koraframework.test.mongo.MongoParams;
import io.opentelemetry.api.trace.TracerProvider;

import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

final class MongoTestUtils {

    private MongoTestUtils() { }

    static MongoDataSource createDataSource(MongoParams params) {
        var config = new $MongoConfig_ConfigValueMapper.MongoConfig_Impl(
            params.connectionString(),
            params.database(),
            null,
            new $MongoConfig_PoolConfig_ConfigValueMapper.PoolConfig_Impl(null, null, null, null, null, null),
            new $MongoConfig_SocketConfig_ConfigValueMapper.SocketConfig_Impl(null, null),
            new $MongoConfig_ClusterConfig_ConfigValueMapper.ClusterConfig_Impl(Duration.ofSeconds(30), null),
            new $MongoConfig_ServerConfig_ConfigValueMapper.ServerConfig_Impl(null, null),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            true,
            true,
            new $DatabaseTelemetryConfig_ConfigValueMapper.DatabaseTelemetryConfig_Impl(
                new $DatabaseTelemetryConfig_DatabaseLoggingConfig_ConfigValueMapper.DatabaseLoggingConfig_Impl(true),
                new $DatabaseTelemetryConfig_DatabaseMetricsConfig_ConfigValueMapper.DatabaseMetricsConfig_Impl(true, true, new Duration[0], Map.of()),
                new $DatabaseTelemetryConfig_DatabaseTracingConfig_ConfigValueMapper.DatabaseTracingConfig_Impl(true, Map.of())
            )
        );
        return new MongoDataSource(
            config,
            new DefaultDatabaseTelemetryFactory(TracerProvider.noop().get(""), NoopMeterRegistry.INSTANCE, NoopDatabaseLoggerFactory.INSTANCE, NoopDatabaseMetricsFactory.INSTANCE),
            null);
    }

    static void withDb(MongoParams params, Consumer<MongoDataSource> consumer) {
        var db = createDataSource(params);
        try {
            db.init();
            consumer.accept(db);
        } finally {
            db.release();
        }
    }
}
