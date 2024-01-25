package ru.tinkoff.kora.micrometer.module.kafka.producer;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import io.opentelemetry.semconv.SemanticAttributes;
import jakarta.annotation.Nullable;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerMetrics;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @see <a href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/messaging/messaging-metrics.md">messaging-metrics</a>
 */
public class MicrometerKafkaProducerMetrics implements KafkaProducerMetrics, AutoCloseable {
    private final KafkaClientMetrics micrometerMetrics;
    private final Properties properties;
    private final TelemetryConfig.MetricsConfig config;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<TopicPartition, DistributionSummary> metrics = new ConcurrentHashMap<>();

    public MicrometerKafkaProducerMetrics(MeterRegistry meterRegistry, TelemetryConfig.MetricsConfig config, Producer<?, ?> producer, Properties properties) {
        this.micrometerMetrics = new KafkaClientMetrics(producer);
        this.micrometerMetrics.bindTo(meterRegistry);
        this.properties = properties;
        this.config = config;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public KafkaProducerTxMetrics tx() {
        return new KafkaProducerTxMetrics() {
            @Override
            public void commit() {

            }

            @Override
            public void rollback(@Nullable Throwable e) {

            }
        };
    }

    @Override
    public void sendEnd(ProducerRecord<?, ?> record, double duration, Throwable e) {
        var m = this.metrics.computeIfAbsent(new TopicPartition(record.topic(), record.partition()), this::metrics);
        m.record(duration);
    }

    @Override
    public void sendEnd(ProducerRecord<?, ?> record, double duration) {
        var m = this.metrics.computeIfAbsent(new TopicPartition(record.topic(), record.partition()), this::metrics);
        m.record(duration);
    }

    @Override
    public void close() throws Exception {
        this.micrometerMetrics.close();
        for (var i = this.metrics.entrySet().iterator(); i.hasNext();) {
            var entry = i.next();
            i.remove();
            try {
                entry.getValue().close();
            } catch (Throwable ignore) {
            }
        }
    }

    private DistributionSummary metrics(TopicPartition topicPartition) {
        var builder = DistributionSummary.builder("messaging.publish.duration")
            .serviceLevelObjectives(this.config.slo())
            .baseUnit("milliseconds")
            .tag(SemanticAttributes.MESSAGING_SYSTEM.getKey(), "kafka")
            .tag(SemanticAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION.getKey(), Integer.toString(topicPartition.partition()))
            .tag(SemanticAttributes.MESSAGING_DESTINATION_NAME.getKey(), topicPartition.topic());
        var clientId = this.properties.get(ProducerConfig.CLIENT_ID_CONFIG);
        if (clientId != null) {
            builder.tag(SemanticAttributes.MESSAGING_CLIENT_ID.getKey(), clientId.toString());
        }
        return builder.register(this.meterRegistry);
    }

}
