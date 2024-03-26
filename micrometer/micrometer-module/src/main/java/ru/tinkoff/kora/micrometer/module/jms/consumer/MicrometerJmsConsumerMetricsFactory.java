package ru.tinkoff.kora.micrometer.module.jms.consumer;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.semconv.SemanticAttributes;
import ru.tinkoff.kora.jms.telemetry.JmsConsumerMetrics;
import ru.tinkoff.kora.jms.telemetry.JmsConsumerMetricsFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

//https://github.com/open-telemetry/semantic-conventions/blob/main/docs/messaging/messaging-metrics.md
public class MicrometerJmsConsumerMetricsFactory implements JmsConsumerMetricsFactory {
    private final MeterRegistry meterRegistry;

    public MicrometerJmsConsumerMetricsFactory(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public JmsConsumerMetrics get(TelemetryConfig.MetricsConfig config, String queueName) {
        var builder = DistributionSummary.builder("messaging.receive.duration")
            .serviceLevelObjectives(config.slo(null))
            .baseUnit("s")
            .tag(SemanticAttributes.MESSAGING_SYSTEM.getKey(), "jms")
            .tag(SemanticAttributes.MESSAGING_DESTINATION_NAME.getKey(), queueName)
            .tag(SemanticAttributes.MESSAGING_DESTINATION_KIND.getKey(), "queue");
        var distributionSummary = builder.register(this.meterRegistry);
        return new MicrometerJmsConsumerMetrics(distributionSummary);
    }
}
