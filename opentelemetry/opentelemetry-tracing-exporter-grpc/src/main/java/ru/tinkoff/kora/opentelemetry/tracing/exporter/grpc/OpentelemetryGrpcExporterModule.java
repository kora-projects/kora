package ru.tinkoff.kora.opentelemetry.tracing.exporter.grpc;

import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import ru.tinkoff.kora.application.graph.LifecycleWrapper;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.ConfigValue;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.opentelemetry.tracing.OpentelemetryTracingModule;

public interface OpentelemetryGrpcExporterModule extends OpentelemetryTracingModule {
    @DefaultComponent
    default LifecycleWrapper<SpanExporter> spanExporter(OpentelemetryGrpcExporterConfig exporterConfig) {
        if (!(exporterConfig instanceof OpentelemetryGrpcExporterConfig.FromConfig config)) {
            return new LifecycleWrapper<>(SpanExporter.composite(), v -> {}, v -> {});
        }

        var exporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint(config.endpoint())
            .setTimeout(config.exportTimeout())
            .build();
        return new LifecycleWrapper<>(exporter, e -> {}, SpanExporter::close);
    }

    default OpentelemetryGrpcExporterConfig otlpGrpcSpanExporterConfig(Config config, ConfigValueExtractor<OpentelemetryGrpcExporterConfig.FromConfig> extractor) {
        var value = config.get("tracing.exporter");
        if (value instanceof ConfigValue.NullValue || value.asObject().get("endpoint").isNull()) {
            return new OpentelemetryGrpcExporterConfig.Empty();
        }
        return extractor.extract(value);
    }

    @DefaultComponent
    default LifecycleWrapper<SpanProcessor> spanProcessor(OpentelemetryGrpcExporterConfig exporterConfig, SpanExporter spanExporter) {
        if (!(exporterConfig instanceof OpentelemetryGrpcExporterConfig.FromConfig config)) {
            return new LifecycleWrapper<>(SpanProcessor.composite(), v -> {}, v -> {});
        }
        var spanProcessor = BatchSpanProcessor.builder(spanExporter)
            .setExporterTimeout(config.exportTimeout())
            .setMaxExportBatchSize(config.maxExportBatchSize())
            .setMaxQueueSize(config.maxQueueSize())
            .setScheduleDelay(config.scheduleDelay())
            .build();
        return new LifecycleWrapper<>(spanProcessor, p -> {}, SpanProcessor::close);
    }
}
