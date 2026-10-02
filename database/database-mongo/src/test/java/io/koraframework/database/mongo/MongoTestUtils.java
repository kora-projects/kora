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

    static MongoConfig config(String uri, String database, Duration serverSelectionTimeout, boolean failFast, boolean readinessProbe, Duration readinessTimeout) {
        return new $MongoConfig_ConfigValueMapper.MongoConfig_Impl(
            uri,
            database,
            null,
            new $MongoConfig_PoolConfig_ConfigValueMapper.PoolConfig_Impl(null, null, null, null, null, null),
            new $MongoConfig_SocketConfig_ConfigValueMapper.SocketConfig_Impl(null, null),
            new $MongoConfig_ClusterConfig_ConfigValueMapper.ClusterConfig_Impl(serverSelectionTimeout, null),
            new $MongoConfig_ServerConfig_ConfigValueMapper.ServerConfig_Impl(null, null),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            failFast,
            readinessProbe,
            readinessTimeout,
            new $DatabaseTelemetryConfig_ConfigValueMapper.DatabaseTelemetryConfig_Impl(
                new $DatabaseTelemetryConfig_DatabaseLoggingConfig_ConfigValueMapper.DatabaseLoggingConfig_Impl(true),
                new $DatabaseTelemetryConfig_DatabaseMetricsConfig_ConfigValueMapper.DatabaseMetricsConfig_Impl(true, true, new Duration[0], Map.of()),
                new $DatabaseTelemetryConfig_DatabaseTracingConfig_ConfigValueMapper.DatabaseTracingConfig_Impl(true, Map.of())
            )
        );
    }

    static MongoDataSource createDataSource(MongoParams params) {
        return createDataSource(config(params.connectionString(), params.database(), Duration.ofSeconds(30), true, true, Duration.ofSeconds(5)));
    }

    static MongoDataSource createDataSource(MongoConfig config) {
        return new MongoDataSource(
            config,
            new DefaultDatabaseTelemetryFactory(TracerProvider.noop().get(""), NoopMeterRegistry.INSTANCE, NoopDatabaseLoggerFactory.INSTANCE, NoopDatabaseMetricsFactory.INSTANCE),
            null);
    }

    static void withDb(MongoParams params, Consumer<MongoDataSource> consumer) {
        withDb(createDataSource(params), consumer);
    }

    static void withDb(MongoDataSource db, Consumer<MongoDataSource> consumer) {
        try {
            db.init();
            consumer.accept(db);
        } finally {
            db.release();
        }
    }
}
