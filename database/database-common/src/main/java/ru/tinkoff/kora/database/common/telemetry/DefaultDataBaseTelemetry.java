package ru.tinkoff.kora.database.common.telemetry;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.database.common.QueryContext;

import javax.annotation.Nullable;

public class DefaultDataBaseTelemetry implements DataBaseTelemetry {
    private static final DataBaseTelemetryContext NOOP_CTX = exception -> {
    };

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
        var metricWriter = this.metricWriter;
        var tracing = this.tracing;
        var logger = this.logger;
        if (metricWriter == null && tracing == null && (logger == null || !logger.isEnabled())) {
            return NOOP_CTX;
        }

        var span = tracing == null ? null : tracing.createQuerySpan(ctx, query);
        var start = System.nanoTime();
        if (logger != null) logger.logQueryBegin(query);

        return exception -> {
            var duration = System.nanoTime() - start;
            if (metricWriter != null) metricWriter.recordQuery(start, query, exception);
            if (logger != null) logger.logQueryEnd(duration, query, exception);
            if (span != null) span.close(exception);
        };
    }
}
