package io.koraframework.database.cassandra;

import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_ConfigValueMapper;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseLoggingConfig_ConfigValueMapper;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseMetricsConfig_ConfigValueMapper;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseTracingConfig_ConfigValueMapper;
import io.koraframework.database.common.telemetry.impl.DefaultDatabaseTelemetryFactory;
import io.koraframework.database.common.telemetry.impl.NoopDatabaseLoggerFactory;
import io.koraframework.database.common.telemetry.impl.NoopDatabaseMetricsFactory;
import io.koraframework.test.cassandra.CassandraParams;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.TracerProvider;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class CassandraTestUtils {
    private CassandraTestUtils() {}

    static CassandraSession createCassandraDataSource(CassandraParams params) {
        var profiles = new HashMap<String, CassandraConfig.Profile>();
        profiles.put(
            "profile",
            new $CassandraConfig_Profile_ConfigValueMapper.Profile_Impl(new $CassandraConfig_Profile_ProfileBasic_ConfigValueMapper.ProfileBasic_Impl(
                List.of(),
                new $CassandraConfig_Basic_BasicRequestConfig_ConfigValueMapper.BasicRequestConfig_Impl(Duration.ofSeconds(10), null, null, null, null),
                null,
                null,
                null,
                null,
                null
            ), null)
        );
        var config = new $CassandraConfig_ConfigValueMapper.CassandraConfig_Impl(
            profiles,
            new $CassandraConfig_Basic_ConfigValueMapper.Basic_Impl(
                null,
                null,
                List.of(params.host() + ":" + params.port()),
                params.dc(),
                params.keyspace(),
                null,
                null
            ),
            new $CassandraConfig_Advanced_ConfigValueMapper.Advanced_Impl(
                null, null, null, null, null, null, null, null, null,
                new $CassandraConfig_Advanced_MetricsConfig_ConfigValueMapper.MetricsConfig_Impl(
                    new $CassandraConfig_Advanced_MetricsConfig_IdGenerator_ConfigValueMapper.IdGenerator_Defaults(),
                    null, null, false
                ),
                null, null, null, null, null, null, null, null, null
            ),
            params.username() == null ? null : new $CassandraConfig_CassandraCredentials_ConfigValueMapper.CassandraCredentials_Impl(
                params.username(),
                params.password()
            ),
            new $DatabaseTelemetryConfig_ConfigValueMapper.DatabaseTelemetryConfig_Impl(
                new $DatabaseTelemetryConfig_DatabaseLoggingConfig_ConfigValueMapper.DatabaseLoggingConfig_Impl(true),
                new $DatabaseTelemetryConfig_DatabaseMetricsConfig_ConfigValueMapper.DatabaseMetricsConfig_Impl(true, true, new Duration[0], Map.of()),
                new $DatabaseTelemetryConfig_DatabaseTracingConfig_ConfigValueMapper.DatabaseTracingConfig_Impl(true, Map.of())
            )
        );
        return new CassandraSession(config, new DefaultDatabaseTelemetryFactory(TracerProvider.noop().get(""), new CompositeMeterRegistry(), NoopDatabaseLoggerFactory.INSTANCE, NoopDatabaseMetricsFactory.INSTANCE), null, null);
    }

    static void withDb(CassandraParams params, Consumer<CassandraSession> consumer) {
        var db = createCassandraDataSource(params);
        try {
            db.init();
            consumer.accept(db);
        } finally {
            db.release();
        }
    }
}
