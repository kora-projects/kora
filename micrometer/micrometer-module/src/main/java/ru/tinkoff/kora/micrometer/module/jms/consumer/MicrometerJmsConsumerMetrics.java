package ru.tinkoff.kora.micrometer.module.jms.consumer;

import io.micrometer.core.instrument.Timer;
import ru.tinkoff.kora.jms.telemetry.JmsConsumerMetrics;

import javax.jms.Message;
import java.util.concurrent.TimeUnit;

public class MicrometerJmsConsumerMetrics implements JmsConsumerMetrics {
    private final Timer distributionSummary;

    public MicrometerJmsConsumerMetrics(Timer distributionSummary) {
        this.distributionSummary = distributionSummary;
    }

    @Override
    public void onMessageProcessed(Message message, long duration) {
        this.distributionSummary.record(duration, TimeUnit.NANOSECONDS);
    }
}
