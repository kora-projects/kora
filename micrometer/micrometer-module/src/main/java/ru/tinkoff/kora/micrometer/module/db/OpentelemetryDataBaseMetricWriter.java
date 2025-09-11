package ru.tinkoff.kora.micrometer.module.db;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.semconv.DbAttributes;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.database.common.QueryContext;
import ru.tinkoff.kora.database.common.telemetry.DataBaseMetricWriter;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class OpentelemetryDataBaseMetricWriter implements DataBaseMetricWriter {

    private final String poolName;
    private final ConcurrentHashMap<DbKey, DbMetrics> metrics = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;
    private final TelemetryConfig.MetricsConfig config;

    public OpentelemetryDataBaseMetricWriter(MeterRegistry meterRegistry, TelemetryConfig.MetricsConfig config, String poolName) {
        this.poolName = poolName;
        this.meterRegistry = meterRegistry;
        this.config = config;
    }

    @Override
    public void recordQuery(long queryBegin, QueryContext queryContext, @Nullable Throwable exception) {
        var duration = System.nanoTime() - queryBegin;
        var key = new DbKey(queryContext.queryId(), queryContext.operation(), exception == null ? null : exception.getClass());
        var metrics = this.metrics.computeIfAbsent(key, this::metrics);
        metrics.duration().record(duration, TimeUnit.NANOSECONDS);
    }

    @Override
    public Object getMetricRegistry() {
        return this.meterRegistry;
    }

    private record DbMetrics(Timer duration) {}

    private record DbKey(String queryId, String operation, @Nullable Class<? extends Throwable> error) {}

    private DbMetrics metrics(DbKey key) {
        var builder = Timer.builder("db.client.operation.duration")
            .serviceLevelObjectives(this.config.slo())
            .tag(DbIncubatingAttributes.DB_CLIENT_CONNECTION_POOL_NAME.getKey(), this.poolName)
            .tag(DbAttributes.DB_QUERY_TEXT.getKey(), key.queryId())
            .tag(DbAttributes.DB_OPERATION_NAME.getKey(), key.operation());

        if (key.error != null) {
            builder.tag(ErrorAttributes.ERROR_TYPE.getKey(), key.error.getCanonicalName());
        } else {
            builder.tag(ErrorAttributes.ERROR_TYPE.getKey(), "");
        }

        return new DbMetrics(builder.register(this.meterRegistry));
    }
}
