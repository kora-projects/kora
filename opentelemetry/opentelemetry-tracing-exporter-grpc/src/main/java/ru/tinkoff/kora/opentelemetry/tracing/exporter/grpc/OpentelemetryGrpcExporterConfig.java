package ru.tinkoff.kora.opentelemetry.tracing.exporter.grpc;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;


public sealed interface OpentelemetryGrpcExporterConfig {
    record Empty() implements OpentelemetryGrpcExporterConfig {}

    @ConfigValueExtractor
    sealed interface FromConfig extends OpentelemetryGrpcExporterConfig permits
        $OpentelemetryGrpcExporterConfig_FromConfig_ConfigValueExtractor.FromConfig_Defaults,
        $OpentelemetryGrpcExporterConfig_FromConfig_ConfigValueExtractor.FromConfig_Impl {

        String endpoint();

        default Duration exportTimeout() {
            return Duration.ofSeconds(2);
        }

        default Duration scheduleDelay() {
            return Duration.ofSeconds(2);
        }

        default int maxExportBatchSize() {
            return 512;
        }

        default int maxQueueSize() {
            return 1024;
        }
    }
}

