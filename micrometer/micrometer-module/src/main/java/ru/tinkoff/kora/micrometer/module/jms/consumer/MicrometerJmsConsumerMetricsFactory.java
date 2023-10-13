package ru.tinkoff.kora.micrometer.module.jms.consumer;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import ru.tinkoff.kora.jms.telemetry.JmsConsumerMetrics;
import ru.tinkoff.kora.jms.telemetry.JmsConsumerMetricsFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public class MicrometerJmsConsumerMetricsFactory implements JmsConsumerMetricsFactory {
    private final MeterRegistry meterRegistry;

    public MicrometerJmsConsumerMetricsFactory(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public JmsConsumerMetrics get(TelemetryConfig.MetricsConfig config, String queueName) {
        // wait for opentelemetry standard https://github.com/open-telemetry/opentelemetry-specification/tree/main/specification/metrics/semantic_conventions
        var builder = DistributionSummary.builder("messaging.consumer.duration")
            .serviceLevelObjectives(config.slo())
            .baseUnit("milliseconds")
            .tag("messaging.system", "jms")
            .tag("messaging.destination", queueName)
            .tag("messaging.destination_kind", "queue");
        var distributionSummary = builder.register(this.meterRegistry);
        return new MicrometerJmsConsumerMetrics(distributionSummary);
    }
}
