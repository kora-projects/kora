package ru.tinkoff.kora.micrometer.module.camunda.engine.bpmn;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.semconv.ErrorAttributes;
import jakarta.annotation.Nullable;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import ru.tinkoff.kora.camunda.engine.bpmn.telemetry.CamundaEngineBpmnMetrics;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class MicrometerCamundaEngineBpmnMetrics implements CamundaEngineBpmnMetrics {

    record RequestKey(String javaDelegateName, String businessKey) {}

    record Key(String javaDelegateName, String businessKey, @Nullable Class<? extends Throwable> errorType) {}

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<RequestKey, AtomicInteger> requestCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Key, Timer> duration = new ConcurrentHashMap<>();
    private final TelemetryConfig.MetricsConfig config;

    public MicrometerCamundaEngineBpmnMetrics(MeterRegistry meterRegistry, TelemetryConfig.MetricsConfig config) {
        this.meterRegistry = meterRegistry;
        this.config = config;
    }

    @Override
    public void executionStarted(String javaDelegateName, DelegateExecution execution) {
        var counter = requestCounters.computeIfAbsent(new RequestKey(javaDelegateName, execution.getProcessBusinessKey()), requestsKey -> {
            var c = new AtomicInteger(0);
            this.registerActiveRequestsGauge(requestsKey, c);
            return c;
        });
        counter.incrementAndGet();
    }

    @Override
    public void executionFinished(String javaDelegateName,
                                  DelegateExecution execution,
                                  long processingTimeNano,
                                  @Nullable Throwable exception) {
        var counter = requestCounters.computeIfAbsent(new RequestKey(javaDelegateName, execution.getProcessBusinessKey()), requestsKey -> {
            var c = new AtomicInteger(0);
            this.registerActiveRequestsGauge(requestsKey, c);
            return c;
        });
        counter.decrementAndGet();

        var errorType = exception != null ? exception.getClass() : null;
        var key = new Key(javaDelegateName, execution.getProcessBusinessKey(), errorType);

        this.duration.computeIfAbsent(key, this::requestDuration)
            .record(processingTimeNano, TimeUnit.NANOSECONDS);
    }

    private void registerActiveRequestsGauge(RequestKey key, AtomicInteger counter) {
        var list = new ArrayList<Tag>(2);
        list.add(Tag.of("delegate", key.javaDelegateName()));
        list.add(Tag.of("business.key", key.businessKey()));

        Gauge.builder("camunda.engine.delegate.active_requests", counter, AtomicInteger::get)
            .tags(list)
            .register(this.meterRegistry);
    }

    private Timer requestDuration(Key key) {
        var list = new ArrayList<Tag>(3);
        if (key.errorType() != null) {
            list.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), key.errorType().getCanonicalName()));
        } else {
            list.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), ""));
        }

        list.add(Tag.of("delegate", key.javaDelegateName()));
        list.add(Tag.of("business.key", key.businessKey()));

        var builder = Timer.builder("camunda.engine.delegate.duration")
            .serviceLevelObjectives(this.config.slo())
            .tags(list);

        return builder.register(this.meterRegistry);
    }
}
