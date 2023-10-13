package ru.tinkoff.kora.micrometer.module.db;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import ru.tinkoff.kora.database.common.QueryContext;
import ru.tinkoff.kora.database.common.telemetry.DataBaseMetricWriter;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.concurrent.ConcurrentHashMap;

public final class MicrometerDataBaseMetricWriter implements DataBaseMetricWriter {

    private final String poolName;
    private final ConcurrentHashMap<DbKey, DbMetrics> metrics = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;
    private final TelemetryConfig.MetricsConfig config;

    public MicrometerDataBaseMetricWriter(MeterRegistry meterRegistry, TelemetryConfig.MetricsConfig config, String poolName) {
        this.poolName = poolName;
        this.meterRegistry = meterRegistry;
        this.config = config;
    }

    @Override
    public void recordQuery(long queryBegin, QueryContext queryContext, Throwable exception) {
        var duration = System.nanoTime() - queryBegin;
        var key = new DbKey(queryContext.queryId(), queryContext.operation());
        var metrics = this.metrics.computeIfAbsent(key, k -> metrics(queryContext));
        metrics.duration().record((double) duration / 1_000_000);
    }

    @Override
    public Object getMetricRegistry() {
        return this.meterRegistry;
    }

    private record DbMetrics(DistributionSummary duration) { }

    private record DbKey(String queryId, String operation) { }

    private DbMetrics metrics(QueryContext queryContext) {
        var builder = DistributionSummary.builder("database.client.request.duration")
                .serviceLevelObjectives(this.config.slo())
                .baseUnit("milliseconds")
                .tag("pool", this.poolName)
                .tag("query.id", queryContext.queryId())
                .tag("query.operation", queryContext.operation());
        return new DbMetrics(builder.register(Metrics.globalRegistry));
    }
}
