package ru.tinkoff.kora.micrometer.module.jms.consumer;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import ru.tinkoff.kora.jms.telemetry.JmsConsumerMetrics;
import ru.tinkoff.kora.jms.telemetry.JmsConsumerMetricsFactory;

public class MicrometerJmsConsumerMetricsFactory implements JmsConsumerMetricsFactory {
    private final MeterRegistry meterRegistry;

    public MicrometerJmsConsumerMetricsFactory(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public JmsConsumerMetrics get(String queueName) {
        // wait for opentelemetry standard https://github.com/open-telemetry/opentelemetry-specification/tree/main/specification/metrics/semantic_conventions
        var distributionSummary = DistributionSummary.builder("messaging.consumer.duration")
            .serviceLevelObjectives(1, 10, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 30000, 60000, 90000)
            .baseUnit("milliseconds")
            .tag("messaging.system", "jms")
            .tag("messaging.destination", queueName)
            .tag("messaging.destination_kind", "queue")
            .register(this.meterRegistry);
        return new MicrometerJmsConsumerMetrics(distributionSummary);
    }
}
