package ru.tinkoff.kora.micrometer.module.jms.consumer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
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
        var builder = Timer.builder("messaging.receive.duration")
            .serviceLevelObjectives(config.slo())
            .tag(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingIncubatingAttributes.MessagingSystemIncubatingValues.JMS)
            .tag(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), queueName);

        var distributionSummary = builder.register(this.meterRegistry);
        return new MicrometerJmsConsumerMetrics(distributionSummary);
    }
}
