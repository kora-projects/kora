package ru.tinkoff.kora.opentelemetry.tracing.exporter.grpc;

import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.common.export.RetryPolicy;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.ConfigValue;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.opentelemetry.tracing.OpentelemetryTracingModule;

public interface OpentelemetryGrpcExporterModule extends OpentelemetryTracingModule {
    @DefaultComponent
    default SpanExporter spanExporter(OpentelemetryGrpcExporterConfig exporterConfig, @Nullable MeterProvider meterProvider) {
        if (!(exporterConfig instanceof OpentelemetryGrpcExporterConfig.FromConfig config)) {
            return SpanExporter.composite();
        }

        var exporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint(config.endpoint())
            .setTimeout(config.exportTimeout())
            .setCompression(config.compression());
        if (meterProvider != null) {
            exporter.setMeterProvider(meterProvider);
        }
        if (config.connectTimeout() != null) {
            exporter.setConnectTimeout(config.connectTimeout());
        }
        var retryPolicy = config.retryPolicy();
        if (retryPolicy != null) {
            exporter.setRetryPolicy(RetryPolicy.builder()
                .setMaxAttempts(retryPolicy.maxAttempts())
                .setMaxBackoff(retryPolicy.maxBackoff())
                .setInitialBackoff(retryPolicy.initialBackoff())
                .setBackoffMultiplier(retryPolicy.backoffMultiplier())
                .build());
        }

        return exporter.build();
    }

    default OpentelemetryGrpcExporterConfig otlpGrpcSpanExporterConfig(Config config, ConfigValueExtractor<OpentelemetryGrpcExporterConfig.FromConfig> extractor) {
        var value = config.get("tracing.exporter");
        if (value instanceof ConfigValue.NullValue || value.asObject().get("endpoint").isNull()) {
            return new OpentelemetryGrpcExporterConfig.Empty();
        }
        return extractor.extract(value);
    }

    @DefaultComponent
    default SpanProcessor spanProcessor(OpentelemetryGrpcExporterConfig exporterConfig, SpanExporter spanExporter, @Nullable MeterProvider meterProvider) {
        if (!(exporterConfig instanceof OpentelemetryGrpcExporterConfig.FromConfig config)) {
            return SpanProcessor.composite();
        }
        var spanProcessor = BatchSpanProcessor.builder(spanExporter)
            .setExporterTimeout(config.batchExportTimeout())
            .setMaxExportBatchSize(config.maxExportBatchSize())
            .setMaxQueueSize(config.maxQueueSize())
            .setScheduleDelay(config.scheduleDelay())
            .setExportUnsampledSpans(config.exportUnsampledSpans());
        if (meterProvider != null) {
            spanProcessor.setMeterProvider(meterProvider);
        }
        return spanProcessor.build();
    }
}
