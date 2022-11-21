package ru.tinkoff.kora.micrometer.module.db;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import ru.tinkoff.kora.database.common.QueryContext;
import ru.tinkoff.kora.database.common.telemetry.DataBaseMetricWriter;

import java.util.concurrent.ConcurrentHashMap;

public final class MicrometerDataBaseMetricWriter implements DataBaseMetricWriter {
    private final String poolName;
    private final ConcurrentHashMap<String, DbMetrics> metrics = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;

    public MicrometerDataBaseMetricWriter(MeterRegistry meterRegistry, String poolName) {
        this.poolName = poolName;
        this.meterRegistry = meterRegistry;
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
        var duration = DistributionSummary.builder("database.client.request.duration")
            .serviceLevelObjectives(1, 10, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 30000, 60000, 90000)
            .baseUnit("milliseconds")
            .tag("pool", this.poolName)
            .tag("query.id", key)
            .register(Metrics.globalRegistry);
        return new DbMetrics(duration);
    }
}
