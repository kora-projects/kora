package io.koraframework.camunda.zeebe.worker.telemetry.impl;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.koraframework.camunda.zeebe.worker.telemetry.ZeebeWorkerObservation;
import io.koraframework.camunda.zeebe.worker.telemetry.ZeebeWorkerTelemetry;
import io.koraframework.telemetry.common.TelemetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;

public class DefaultZeebeWorkerTelemetry implements ZeebeWorkerTelemetry {

    public record TelemetryContext(TelemetryConfig config,
                                   String workerType,
                                   boolean isTraceEnabled,
                                   boolean isMetricsEnabled,
                                   MeterRegistry meterRegistry,
                                   Tracer tracer) {

        public static final TelemetryContext EMPTY = new TelemetryContext(
            new TelemetryConfig() {
                @Override
                public LogConfig logging() {
                    return new LogConfig() {};
                }

                @Override
                public TracingConfig tracing() {
                    return new TracingConfig() {};
                }

                @Override
                public MetricsConfig metrics() {
                    return new MetricsConfig() {};
                }
            },
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

    public DefaultZeebeWorkerTelemetry(TelemetryConfig config,
                                       String workerType,
                                       Tracer tracer,
                                       MeterRegistry meterRegistry,
                                       DefaultZeebeWorkerMetricsFactory metricsFactory,
                                       DefaultZeebeWorkerLoggerFactory loggerFactory) {
        var isTraceEnabled = config.tracing().enabled() && tracer != DefaultZeebeWorkerTelemetryFactory.NOOP_TRACER;
        var isMetricsEnabled = config.metrics().enabled() && meterRegistry != DefaultZeebeWorkerTelemetryFactory.NOOP_METER_REGISTRY;
        this.context = new TelemetryContext(config, workerType, isTraceEnabled, isMetricsEnabled, meterRegistry, tracer);
        this.logger = loggerFactory.create(this.context);
        this.metrics = metricsFactory.create(this.context);
    }

    @Override
    public ZeebeWorkerObservation observe(ActivatedJob job) {
        var span = this.createSpan(job);
        return new DefaultZeebeWorkerObservation(job, context, span, logger, metrics);
    }

    protected Span createSpan(ActivatedJob job) {
        if (!this.context.isTraceEnabled()) {
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
