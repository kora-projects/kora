package ru.tinkoff.kora.camunda.zeebe.worker.telemetry;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.noop.NoopTimer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DefaultZeebeWorkerTelemetry implements ZeebeWorkerTelemetry {
    private static final Timer NOOP_TIMER = new NoopTimer(new Meter.Id("noop", Tags.empty(), null, null, Meter.Type.TIMER));

    protected final TelemetryConfig config;
    protected final String workerType;
    protected final Tracer tracer;
    protected final MeterRegistry meterRegistry;
    protected final ConcurrentMap<DurationKey, ConcurrentMap<Tags, Timer>> durationCache = new ConcurrentHashMap<>();
    protected final Logger logger;

    protected record DurationKey(String jobType) {}

    public DefaultZeebeWorkerTelemetry(TelemetryConfig config,
                                       String workerType,
                                       Tracer tracer,
                                       MeterRegistry meterRegistry) {
        this.config = config;
        this.workerType = workerType;
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        if (config.logging().enabled()) {
            this.logger = LoggerFactory.getLogger("ru.tinkoff.kora.camunda.zeebe.worker." + workerType);
        } else {
            this.logger = NOPLogger.NOP_LOGGER;
        }
    }

    @Override
    public ZeebeWorkerObservation observe(ActivatedJob job) {
        var duration = this.duration(job);
        var span = this.createSpan(job);

        return new DefaultZeebeWorkerObservation(job, span, duration, this.logger);
    }

    protected Span createSpan(ActivatedJob job) {
        if (!this.config.tracing().enabled()) {
            return Span.getInvalid();
        }
        var b = this.tracer
            .spanBuilder("Zeebe Worker " + workerType)
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("jobType", job.getType())
            .setAttribute("jobName", this.workerType)
            .setAttribute("jobKey", job.getKey())
            .setAttribute("jobWorker", job.getWorker())
            .setAttribute("processKey", job.getProcessDefinitionKey())
            .setAttribute("elementId", job.getElementId());
        for (var entry : this.config.tracing().attributes().entrySet()) {
            b.setAttribute(entry.getKey(), entry.getValue());
        }

        return b.startSpan();
    }

    protected Meter.MeterProvider<Timer> duration(ActivatedJob job) {
        if (!this.config.metrics().enabled()) {
            return _ -> NOOP_TIMER;
        }
        var jobType = job.getType();
        var jobDurationCache = durationCache.computeIfAbsent(new DurationKey(jobType), _ -> new ConcurrentHashMap<>());

        return tags -> jobDurationCache.computeIfAbsent(Tags.of(tags), t -> {
            var b = Timer.builder("zeebe.worker.handler.duration")
                .tags(List.of(
                    Tag.of("job.name", workerType),
                    Tag.of("job.type", jobType)
                ))
                .serviceLevelObjectives(this.config.metrics().slo());
            for (var e : this.config.metrics().tags().entrySet()) {
                b.tag(e.getKey(), e.getValue());
            }
            b.tags(t);
            return b.register(this.meterRegistry);
        });

    }
}
