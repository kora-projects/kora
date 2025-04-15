package ru.tinkoff.kora.opentelemetry.tracing.exporter.http;

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.common.export.RetryPolicy;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import ru.tinkoff.kora.application.graph.LifecycleWrapper;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.ConfigValue;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.opentelemetry.tracing.OpentelemetryTracingModule;

public interface OpentelemetryHttpExporterModule extends OpentelemetryTracingModule {
    @DefaultComponent
    default LifecycleWrapper<SpanExporter> spanExporter(OpentelemetryHttpExporterConfig exporterConfig) {
        if (!(exporterConfig instanceof OpentelemetryHttpExporterConfig.FromConfig config)) {
            return new LifecycleWrapper<>(SpanExporter.composite(), v -> {}, v -> {});
        }
        var exporter = OtlpHttpSpanExporter.builder()
            .setEndpoint(config.endpoint())
            .setTimeout(config.exportTimeout())
            .setCompression(config.compression());

        if (config.connectTimeout() != null) {
            exporter.setConnectTimeout(config.connectTimeout());
        }
        if (config.compression() != null) {
            exporter.setCompression(config.compression());
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

        return new LifecycleWrapper<>(exporter.build(), e -> {}, SpanExporter::close);
    }

    default OpentelemetryHttpExporterConfig otlpGrpcSpanExporterConfig(Config config, ConfigValueExtractor<OpentelemetryHttpExporterConfig.FromConfig> extractor) {
        var value = config.get("tracing.exporter");
        if (value instanceof ConfigValue.NullValue || value.asObject().get("endpoint").isNull()) {
            return new OpentelemetryHttpExporterConfig.Empty();
        }
        return extractor.extract(value);
    }

    @DefaultComponent
    default LifecycleWrapper<SpanProcessor> spanProcessor(OpentelemetryHttpExporterConfig exporterConfig, SpanExporter spanExporter) {
        if (!(exporterConfig instanceof OpentelemetryHttpExporterConfig.FromConfig config)) {
            return new LifecycleWrapper<>(SpanProcessor.composite(), v -> {}, v -> {});
        }
        var spanProcessor = BatchSpanProcessor.builder(spanExporter)
            .setExporterTimeout(config.batchExportTimeout())
            .setMaxExportBatchSize(config.maxExportBatchSize())
            .setMaxQueueSize(config.maxQueueSize())
            .setScheduleDelay(config.scheduleDelay())
            .setExportUnsampledSpans(config.exportUnsampledSpans())
            .build();
        return new LifecycleWrapper<>(spanProcessor, p -> {}, SpanProcessor::close);
    }
}
