package ru.tinkoff.kora.opentelemetry.tracing.exporter.grpc;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Objects;


public sealed interface OpentelemetryGrpcExporterConfig {
    record Empty() implements OpentelemetryGrpcExporterConfig {}

    record FromConfig(
        String endpoint,
        Duration exportTimeout,
        Duration scheduleDelay,
        int maxExportBatchSize,
        int maxQueueSize

    ) implements OpentelemetryGrpcExporterConfig {
        public FromConfig(String endpoint, @Nullable Duration exportTimeout, @Nullable Duration scheduleDelay, @Nullable Integer maxExportBatchSize, @Nullable Integer maxQueueSize) {
            this(
                endpoint,
                Objects.requireNonNullElse(exportTimeout, Duration.ofSeconds(2)),
                Objects.requireNonNullElse(scheduleDelay, Duration.ofSeconds(2)),
                Objects.requireNonNullElse(maxExportBatchSize, 512).intValue(),
                Objects.requireNonNullElse(maxQueueSize, 1024).intValue()
            );
        }
    }
}

