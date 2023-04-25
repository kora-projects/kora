package ru.tinkoff.kora.database.common.telemetry;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.database.common.QueryContext;

import javax.annotation.Nullable;

public class DefaultDataBaseTelemetry implements DataBaseTelemetry {
    @Nullable
    private final DataBaseMetricWriter metricWriter;
    @Nullable
    private final DataBaseTracer tracing;
    @Nullable
    private final DataBaseLogger logger;

    public DefaultDataBaseTelemetry(@Nullable DataBaseMetricWriter metricWriter, @Nullable DataBaseTracer tracing, @Nullable DataBaseLogger logger) {
        this.metricWriter = metricWriter;
        this.tracing = tracing;
        this.logger = logger;
    }

    @Override
    public Object getMetricRegistry() {
        if (this.metricWriter == null) {
            return null;
        }
        return this.metricWriter.getMetricRegistry();
    }

    @Override
    public DataBaseTelemetryContext createContext(Context ctx, QueryContext query) {
        var span = this.tracing == null ? null : this.tracing.createQuerySpan(ctx, query);
        var start = System.nanoTime();
        if (this.logger != null) this.logger.logQueryBegin(query);

        return exception -> {
            var duration = System.nanoTime() - start;
            if (this.metricWriter != null) this.metricWriter.recordQuery(start, query, exception);
            if (this.logger != null) this.logger.logQueryEnd(duration, query, exception);
            if (span != null) span.close(exception);
        };
    }
}
