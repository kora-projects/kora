package io.koraframework.opentelemetry.tracing.exporter.http;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.opentelemetry.tracing.OpentelemetryTracingConfig;
import io.koraframework.opentelemetry.tracing.OpentelemetryTracingModule;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.common.export.RetryPolicy;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.jspecify.annotations.Nullable;

public interface OpentelemetryHttpExporterModule extends OpentelemetryTracingModule {

    default OpentelemetryHttpExporterConfig otlpGrpcSpanExporterConfig(Config config, ConfigValueMapper<OpentelemetryHttpExporterConfig> mapper) {
        return mapper.mapOrThrow(config.get("tracing.exporter"));
    }

    @DefaultComponent
    default SpanExporter spanExporter(OpentelemetryHttpExporterConfig exporterConfig,
                                      OpentelemetryTracingConfig tracingConfig,
                                      @Nullable MeterProvider meterProvider) {
        if (exporterConfig.endpoint() == null || !tracingConfig.enabled()) {
            return SpanExporter.composite();
        }

        var exporter = OtlpHttpSpanExporter.builder()
            .setEndpoint(exporterConfig.endpoint())
            .setTimeout(exporterConfig.exportTimeout())
            .setCompression(exporterConfig.compression());
        if (meterProvider != null) {
            exporter.setMeterProvider(meterProvider);
        }
        if (exporterConfig.connectTimeout() != null) {
            exporter.setConnectTimeout(exporterConfig.connectTimeout());
        }
        if (exporterConfig.compression() != null) {
            exporter.setCompression(exporterConfig.compression());
        }
        var retryPolicy = exporterConfig.retryPolicy();
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

    @DefaultComponent
    default SpanProcessor spanProcessor(OpentelemetryHttpExporterConfig exporterConfig,
                                        OpentelemetryTracingConfig tracingConfig,
                                        SpanExporter spanExporter,
                                        @Nullable MeterProvider meterProvider) {
        if (exporterConfig.endpoint() == null || !tracingConfig.enabled()) {
            return SpanProcessor.composite();
        }

        var spanProcessor = BatchSpanProcessor.builder(spanExporter)
            .setExporterTimeout(exporterConfig.batchExportTimeout())
            .setMaxExportBatchSize(exporterConfig.maxExportBatchSize())
            .setMaxQueueSize(exporterConfig.maxQueueSize())
            .setScheduleDelay(exporterConfig.scheduleDelay())
            .setExportUnsampledSpans(exporterConfig.exportUnsampledSpans());
        if (meterProvider != null) {
            spanProcessor.setMeterProvider(meterProvider);
        }
        return spanProcessor.build();
    }
}
