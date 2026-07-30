package io.koraframework.opentelemetry.tracing.exporter.grpc;

import io.koraframework.config.common.annotation.ConfigMapper;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

@ConfigMapper
public interface OpentelemetryGrpcExporterConfig {

    @Nullable
    String endpoint();

    default Duration exportTimeout() {
        return Duration.ofSeconds(3);
    }

    default Duration batchExportTimeout() {
        return Duration.ofSeconds(30);
    }

    @Nullable
    Duration connectTimeout();

    default String compression() {
        return "gzip";
    }

    RetryPolicy retryPolicy();

    default Duration scheduleDelay() {
        return Duration.ofSeconds(2);
    }

    default int maxExportBatchSize() {
        return 512;
    }

    default int maxQueueSize() {
        return 2048;
    }

    default boolean exportUnsampledSpans() {
        return false;
    }

    @ConfigMapper
    interface RetryPolicy {

        default int maxAttempts() {
            return 5;
        }

        default Duration initialBackoff() {
            return Duration.ofSeconds(1);
        }

        default Duration maxBackoff() {
            return Duration.ofSeconds(5);
        }

        default double backoffMultiplier() {
            return 1.5d;
        }
    }
}

