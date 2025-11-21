package ru.tinkoff.kora.camunda.engine.bpmn.telemetry;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.noop.NoopTimer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;
import ru.tinkoff.kora.camunda.engine.bpmn.CamundaEngineBpmnConfig;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DefaultCamundaEngineBpmnTelemetry implements CamundaEngineBpmnTelemetry {
    private static final Timer NOOP_TIMER = new NoopTimer(new Meter.Id("noop", Tags.empty(), null, null, Meter.Type.TIMER));

    private final CamundaEngineBpmnConfig.CamundaTelemetryConfig config;
    private final Tracer tracer;
    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, ConcurrentMap<Tags, Timer>> durationCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Logger> loggerCache = new ConcurrentHashMap<>();

    public DefaultCamundaEngineBpmnTelemetry(CamundaEngineBpmnConfig.CamundaTelemetryConfig config, Tracer tracer, MeterRegistry meterRegistry) {
        this.config = config;
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public CamundaEngineObservation observe(String javaDelegateName) {
        var span = this.createSpan(javaDelegateName);
        var duration = this.createDuration(javaDelegateName);
        var logger = this.createLogger(javaDelegateName);

        return new DefaultCamundaEngineBpmnObservation(config, span, duration, logger);
    }

    protected Logger createLogger(String javaDelegateName) {
        if (!this.config.logging().enabled()) {
            return NOPLogger.NOP_LOGGER;
        }
        return this.loggerCache.computeIfAbsent(javaDelegateName, LoggerFactory::getLogger);
    }

    protected Meter.MeterProvider<Timer> createDuration(String javaDelegateName) {
        if (!this.config.metrics().enabled()) {
            return _ -> NOOP_TIMER;
        }
        var cache = durationCache.computeIfAbsent(javaDelegateName, _ -> new ConcurrentHashMap<>());

        return tags -> cache.computeIfAbsent(Tags.of(tags), t -> {
            var tagsArray = new ArrayList<Tag>();
            tagsArray.add(Tag.of("delegate", javaDelegateName));
            for (var entry : this.config.metrics().tags().entrySet()) {
                tagsArray.add(Tag.of(entry.getKey(), entry.getValue()));
            }
            for (var tag : t) {
                tagsArray.add(tag);
            }
            var builder = Timer.builder("camunda.engine.delegate.duration")
                .serviceLevelObjectives(this.config.metrics().slo())
                .tags(tagsArray);

            return builder.register(this.meterRegistry);
        });
    }

    protected Span createSpan(String javaDelegateName) {
        if (!this.config.tracing().enabled()) {
            return Span.getInvalid();
        }
        var span = this.tracer
            .spanBuilder("Camunda Delegate " + javaDelegateName)
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("delegate", javaDelegateName);
        for (var entry : this.config.tracing().attributes().entrySet()) {
            span.setAttribute(entry.getKey(), entry.getValue());
        }
        return span.startSpan();
    }
}
