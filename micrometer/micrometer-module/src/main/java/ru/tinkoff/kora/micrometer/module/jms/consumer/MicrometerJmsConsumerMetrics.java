package ru.tinkoff.kora.micrometer.module.jms.consumer;

import io.micrometer.core.instrument.DistributionSummary;
import ru.tinkoff.kora.jms.telemetry.JmsConsumerMetrics;

import javax.jms.Message;

public class MicrometerJmsConsumerMetrics implements JmsConsumerMetrics {
    private final DistributionSummary distributionSummary;

    public MicrometerJmsConsumerMetrics(DistributionSummary distributionSummary) {
        this.distributionSummary = distributionSummary;
    }

    @Override
    public void onMessageProcessed(Message message, long duration) {
        this.distributionSummary.record(((double) duration) / 1_000_000);
    }
}
