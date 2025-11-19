package ru.tinkoff.kora.micrometer.module;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.contrib.metrics.micrometer.CallbackRegistrar;
import io.opentelemetry.contrib.metrics.micrometer.MicrometerMeterProvider;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.micrometer.module.camunda.engine.bpmn.MicrometerCamundaEngineBpmnMetricsFactory;
import ru.tinkoff.kora.micrometer.module.camunda.zeebe.worker.MicrometerZeebeWorkerMetricsFactory;
import ru.tinkoff.kora.micrometer.module.resilient.MicrometerCircuitBreakerMetrics;
import ru.tinkoff.kora.micrometer.module.resilient.MicrometerFallbackMetrics;
import ru.tinkoff.kora.micrometer.module.resilient.MicrometerRetryMetrics;
import ru.tinkoff.kora.micrometer.module.resilient.MicrometerTimeoutMetrics;

public interface MetricsModule {
    @Root
    default PrometheusMeterRegistryWrapper prometheusMeterRegistry(All<PrometheusMeterRegistryInitializer> initializers) {
        return new PrometheusMeterRegistryWrapper(initializers);
    }

    @DefaultComponent
    default MicrometerCircuitBreakerMetrics micrometerCircuitBreakerMetrics(MeterRegistry meterRegistry) {
        return new MicrometerCircuitBreakerMetrics(meterRegistry);
    }

    @DefaultComponent
    default MicrometerFallbackMetrics micrometerFallbackMetrics(MeterRegistry meterRegistry) {
        return new MicrometerFallbackMetrics(meterRegistry);
    }

    @DefaultComponent
    default MicrometerRetryMetrics micrometerRetryMetrics(MeterRegistry meterRegistry) {
        return new MicrometerRetryMetrics(meterRegistry);
    }

    @DefaultComponent
    default MicrometerTimeoutMetrics micrometerTimeoutMetrics(MeterRegistry meterRegistry) {
        return new MicrometerTimeoutMetrics(meterRegistry);
    }

    @DefaultComponent
    default MicrometerCamundaEngineBpmnMetricsFactory micrometerCamundaEngineBpmnMetricsFactory(MeterRegistry meterRegistry) {
        return new MicrometerCamundaEngineBpmnMetricsFactory(meterRegistry);
    }

    @DefaultComponent
    default MicrometerZeebeWorkerMetricsFactory micrometerZeebeWorkerMetricsFactory(MeterRegistry meterRegistry) {
        return new MicrometerZeebeWorkerMetricsFactory(meterRegistry);
    }

    @DefaultComponent
    default MicrometerMeterProvider micrometerMeterProvider(MeterRegistry registry, @Nullable CallbackRegistrar callbackRegistrar) {
        return MicrometerMeterProvider.builder(registry)
            .setCallbackRegistrar(callbackRegistrar)
            .build();
    }
}
