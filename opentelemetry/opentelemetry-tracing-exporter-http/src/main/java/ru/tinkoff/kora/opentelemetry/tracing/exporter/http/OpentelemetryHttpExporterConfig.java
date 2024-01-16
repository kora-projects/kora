package ru.tinkoff.kora.opentelemetry.tracing.exporter.http;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;


public sealed interface OpentelemetryHttpExporterConfig {
    record Empty() implements OpentelemetryHttpExporterConfig {}

    @ConfigValueExtractor
    sealed interface FromConfig extends OpentelemetryHttpExporterConfig permits
        $OpentelemetryHttpExporterConfig_FromConfig_ConfigValueExtractor.FromConfig_Defaults,
        $OpentelemetryHttpExporterConfig_FromConfig_ConfigValueExtractor.FromConfig_Impl {

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

