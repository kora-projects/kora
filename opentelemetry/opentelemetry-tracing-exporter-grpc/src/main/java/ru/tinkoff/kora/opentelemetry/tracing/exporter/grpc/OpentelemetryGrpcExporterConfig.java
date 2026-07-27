package ru.tinkoff.kora.opentelemetry.tracing.exporter.grpc;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;


public sealed interface OpentelemetryGrpcExporterConfig {
    record Empty() implements OpentelemetryGrpcExporterConfig {}

    @ConfigValueExtractor
    sealed interface FromConfig extends OpentelemetryGrpcExporterConfig permits
        $OpentelemetryGrpcExporterConfig_FromConfig_ConfigValueExtractor.FromConfig_Defaults,
        $OpentelemetryGrpcExporterConfig_FromConfig_ConfigValueExtractor.FromConfig_Impl {

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
    }

    @ConfigValueExtractor
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

