package io.koraframework.opentelemetry.tracing.exporter.grpc;

import io.koraframework.config.common.annotation.ConfigMapper;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

@ConfigMapper
public interface OpentelemetryGrpcExporterConfig {

    @Nullable
        /**
         * @return OpenTelemetry Collector endpoint where traces are exported over OTLP/gRPC.
         */
    String endpoint();

        /**
         * @return Maximum time to wait while the exporter sends data.
         */
    default Duration exportTimeout() {
        return Duration.ofSeconds(3);
    }

        /**
         * @return Maximum time to wait for one accumulated batch of spans to be exported.
         */
    default Duration batchExportTimeout() {
        return Duration.ofSeconds(30);
    }

        /**
         * @return Timeout for establishing a connection to the exporter.
         */
    @Nullable
    Duration connectTimeout();

        /**
         * @return Data compression used during export, gzip or none.
         */
    default String compression() {
        return "gzip";
    }

        /**
         * @return Retry policy applied to failed export attempts.
         */
    RetryPolicy retryPolicy();

        /**
         * @return Delay between sending accumulated spans to the collector.
         */
    default Duration scheduleDelay() {
        return Duration.ofSeconds(2);
    }

        /**
         * @return Maximum number of spans in one export batch.
         */
    default int maxExportBatchSize() {
        return 512;
    }

        /**
         * @return Maximum queue size for spans waiting to be sent.
         */
    default int maxQueueSize() {
        return 2048;
    }

        /**
         * @return Whether to export spans that were not selected by the Sampler.
         */
    default boolean exportUnsampledSpans() {
        return false;
    }

    @ConfigMapper
    interface RetryPolicy {

        /**
         * @return Maximum number of retry attempts.
         */
        default int maxAttempts() {
            return 5;
        }

        /**
         * @return Initial delay before a retry attempt.
         */
        default Duration initialBackoff() {
            return Duration.ofSeconds(1);
        }

        /**
         * @return Maximum delay before a retry attempt.
         */
        default Duration maxBackoff() {
            return Duration.ofSeconds(5);
        }

        /**
         * @return Delay multiplier between retry attempts.
         */
        default double backoffMultiplier() {
            return 1.5d;
        }
    }
}

