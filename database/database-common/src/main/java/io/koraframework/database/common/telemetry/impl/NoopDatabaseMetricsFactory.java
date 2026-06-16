package io.koraframework.database.common.telemetry.impl;

import io.koraframework.database.common.QueryContext;
import org.jspecify.annotations.Nullable;

public final class NoopDatabaseMetricsFactory extends DefaultDatabaseMetricsFactory {

    public static final NoopDatabaseMetricsFactory INSTANCE = new NoopDatabaseMetricsFactory();

    private NoopDatabaseMetricsFactory() {}

    @Override
    public DefaultDatabaseMetrics create(DefaultDatabaseTelemetry.TelemetryContext context) {
        return NoopDatabaseMetrics.INSTANCE;
    }

    public static final class NoopDatabaseMetrics extends DefaultDatabaseMetrics {

        public static final NoopDatabaseMetrics INSTANCE = new NoopDatabaseMetrics();

        private NoopDatabaseMetrics() {
            super(DefaultDatabaseTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void record(QueryContext query, @Nullable Throwable error, long processingTimeNanos) {

        }
    }
}
