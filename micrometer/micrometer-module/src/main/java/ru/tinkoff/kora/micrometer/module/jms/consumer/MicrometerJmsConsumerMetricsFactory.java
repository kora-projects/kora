package ru.tinkoff.kora.micrometer.module.jms.consumer;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import ru.tinkoff.kora.jms.telemetry.JmsConsumerMetrics;
import ru.tinkoff.kora.jms.telemetry.JmsConsumerMetricsFactory;
import ru.tinkoff.kora.micrometer.module.MetricsConfig.JmsConsumerMetricsConfig;

import javax.annotation.Nullable;

public class MicrometerJmsConsumerMetricsFactory implements JmsConsumerMetricsFactory {
    private final MeterRegistry meterRegistry;
    private final JmsConsumerMetricsConfig config;

    public MicrometerJmsConsumerMetricsFactory(MeterRegistry meterRegistry, @Nullable JmsConsumerMetricsConfig config) {
        this.meterRegistry = meterRegistry;
        this.config = config;
    }

    @Override
    public JmsConsumerMetrics get(String queueName) {
        // wait for opentelemetry standard https://github.com/open-telemetry/opentelemetry-specification/tree/main/specification/metrics/semantic_conventions
        var builder = DistributionSummary.builder("messaging.consumer.duration");
        if (this.config != null && this.config.slo() != null) {
            builder.serviceLevelObjectives(this.config.slo().stream().mapToDouble(Double::doubleValue).toArray());
        } else {
            builder.serviceLevelObjectives(1, 10, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 30000, 60000, 90000);
        }
        var distributionSummary = builder
            .baseUnit("milliseconds")
            .tag("messaging.system", "jms")
            .tag("messaging.destination", queueName)
            .tag("messaging.destination_kind", "queue")
            .register(this.meterRegistry);
        return new MicrometerJmsConsumerMetrics(distributionSummary);
    }
}
