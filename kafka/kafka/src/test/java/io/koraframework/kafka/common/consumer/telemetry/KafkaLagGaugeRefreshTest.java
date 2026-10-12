package io.koraframework.kafka.common.consumer.telemetry;

import io.koraframework.kafka.common.consumer.telemetry.impl.DefaultKafkaConsumerMetricsFactory;
import io.koraframework.kafka.common.consumer.telemetry.impl.DefaultKafkaConsumerTelemetry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.trace.TracerProvider;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaLagGaugeRefreshTest {

    @Test
    void lagGaugeReportsConsumerRecreatedByConfigRefresh() {
        var registry = new SimpleMeterRegistry();
        var partition = new TopicPartition("orders", 0);

        var oldMetrics = metrics(registry);
        oldMetrics.reportTopicLag(partition, 100);

        // a listener config refresh recreates the container and its telemetry with the same tags
        var newMetrics = metrics(registry);
        newMetrics.reportTopicLag(partition, 5);

        assertThat(registry.find("messaging.kafka.consumer.lag").gauges()).hasSize(1);
        assertThat(registry.get("messaging.kafka.consumer.lag").gauge().value()).isEqualTo(5.0);
    }

    private static DefaultKafkaConsumerMetricsFactory.DefaultKafkaConsumerMetrics metrics(MeterRegistry registry) {
        var props = new Properties();
        props.setProperty("group.id", "g");
        return DefaultKafkaConsumerMetricsFactory.INSTANCE.create(new DefaultKafkaConsumerTelemetry.TelemetryContext(
            DefaultKafkaConsumerTelemetry.TelemetryContext.EMPTY.config(), false, true, registry, TracerProvider.noop().get("t"),
            props, "kafka.listener", "a.Listener", "Listener", "", "g"));
    }
}
