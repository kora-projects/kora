package ru.tinkoff.kora.micrometer.module.db;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import ru.tinkoff.kora.database.common.QueryContext;
import ru.tinkoff.kora.database.common.telemetry.DataBaseMetricWriter;
import ru.tinkoff.kora.micrometer.module.MetricsConfig.DbMetricsConfig;

import java.util.concurrent.ConcurrentHashMap;

public final class MicrometerDataBaseMetricWriter implements DataBaseMetricWriter {
    private final String poolName;
    private final ConcurrentHashMap<String, DbMetrics> metrics = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;
    private final DbMetricsConfig config;

    public MicrometerDataBaseMetricWriter(MeterRegistry meterRegistry, DbMetricsConfig config, String poolName) {
        this.poolName = poolName;
        this.meterRegistry = meterRegistry;
        this.config = config;
    }

    @Override
    public void recordQuery(long queryBegin, QueryContext queryContext, Throwable exception) {
        var duration = System.nanoTime() - queryBegin;
        var metrics = this.metrics.computeIfAbsent(queryContext.queryId(), this::metrics);
        metrics.duration().record((double) duration / 1_000_000);
    }

    @Override
    public Object getMetricRegistry() {
        return this.meterRegistry;
    }

    private record DbMetrics(DistributionSummary duration) {}

    private DbMetrics metrics(String key) {
        var builder = DistributionSummary.builder("database.client.request.duration")
            .serviceLevelObjectives(this.config.slo())
            .baseUnit("milliseconds")
            .tag("pool", this.poolName)
            .tag("query.id", key);
        return new DbMetrics(builder.register(Metrics.globalRegistry));
    }
}
