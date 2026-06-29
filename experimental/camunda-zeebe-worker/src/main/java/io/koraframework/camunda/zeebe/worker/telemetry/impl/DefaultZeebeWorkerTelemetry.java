package io.koraframework.camunda.zeebe.worker.telemetry.impl;

import io.camunda.client.api.response.ActivatedJob;
import io.koraframework.camunda.zeebe.worker.telemetry.*;
import io.koraframework.telemetry.common.TelemetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;

public class DefaultZeebeWorkerTelemetry implements ZeebeWorkerTelemetry {

    public record TelemetryContext(ZeebeWorkerTelemetryConfig config,
                                   String workerType,
                                   boolean isTracingEnabled,
                                   boolean isMetricsEnabled,
                                   MeterRegistry meterRegistry,
                                   Tracer tracer) {

        public static final TelemetryContext EMPTY = new TelemetryContext(
            new $ZeebeWorkerTelemetryConfig_ConfigValueExtractor.ZeebeWorkerTelemetryConfig_Impl(
                new $ZeebeWorkerTelemetryConfig_ZeebeWorkerLoggingConfig_ConfigValueExtractor.ZeebeWorkerLoggingConfig_Defaults(),
                new $ZeebeWorkerTelemetryConfig_ZeebeWorkerMetricsConfig_ConfigValueExtractor.ZeebeWorkerMetricsConfig_Defaults(),
                new $ZeebeWorkerTelemetryConfig_ZeebeWorkerTracingConfig_ConfigValueExtractor.ZeebeWorkerTracingConfig_Defaults()
            ),
            "none",
            false,
            false,
            DefaultZeebeWorkerTelemetryFactory.NOOP_METER_REGISTRY,
            DefaultZeebeWorkerTelemetryFactory.NOOP_TRACER
        );
    }

    protected final TelemetryContext context;
    protected final DefaultZeebeWorkerLoggerFactory.DefaultZeebeWorkerLogger logger;
    protected final DefaultZeebeWorkerMetricsFactory.DefaultZeebeWorkerMetrics metrics;

    public DefaultZeebeWorkerTelemetry(ZeebeWorkerTelemetryConfig config,
                                       String workerType,
                                       Tracer tracer,
                                       MeterRegistry meterRegistry,
                                       DefaultZeebeWorkerMetricsFactory metricsFactory,
                                       DefaultZeebeWorkerLoggerFactory loggerFactory) {
        var isTracingEnabled = config.tracing().enabled() && tracer != DefaultZeebeWorkerTelemetryFactory.NOOP_TRACER;
        var isMetricsEnabled = config.metrics().enabled() && meterRegistry != DefaultZeebeWorkerTelemetryFactory.NOOP_METER_REGISTRY;
        this.context = new TelemetryContext(config, workerType, isTracingEnabled, isMetricsEnabled, meterRegistry, tracer);
        this.logger = loggerFactory.create(this.context);
        this.metrics = metricsFactory.create(this.context);
    }

    @Override
    public ZeebeWorkerObservation observe(ActivatedJob job) {
        var span = this.createSpan(job);
        return new DefaultZeebeWorkerObservation(job, context, span, logger, metrics);
    }

    protected Span createSpan(ActivatedJob job) {
        if (!this.context.isTracingEnabled()) {
            return Span.getInvalid();
        }
        var b = this.context.tracer()
            .spanBuilder("Zeebe Worker " + this.context.workerType())
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("jobType", job.getType())
            .setAttribute("jobName", this.context.workerType())
            .setAttribute("jobKey", job.getKey())
            .setAttribute("jobWorker", job.getWorker())
            .setAttribute("processKey", job.getProcessDefinitionKey())
            .setAttribute("elementId", job.getElementId());
        for (var entry : this.context.config().tracing().attributes().entrySet()) {
            b.setAttribute(entry.getKey(), entry.getValue());
        }

        return b.startSpan();
    }
}
