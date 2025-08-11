package ru.tinkoff.kora.micrometer.module.kafka.producer;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import jakarta.annotation.Nullable;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerMetrics;
import ru.tinkoff.kora.micrometer.module.kafka.producer.tag.MicrometerKafkaProducerTagsProvider;
import ru.tinkoff.kora.micrometer.module.kafka.producer.tag.RecordDurationKey;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @see <a href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/messaging/messaging-metrics.md">messaging-metrics</a>
 */
public class Opentelemetry120KafkaProducerMetrics implements KafkaProducerMetrics, AutoCloseable {

    private final ConcurrentHashMap<RecordDurationKey, DistributionSummary> metrics = new ConcurrentHashMap<>();

    private final KafkaClientMetrics micrometerMetrics;
    private final Properties driverProperties;
    private final TelemetryConfig.MetricsConfig config;
    private final MeterRegistry meterRegistry;
    private final MicrometerKafkaProducerTagsProvider tagsProvider;
    @Nullable
    private final String clientId;

    public Opentelemetry120KafkaProducerMetrics(MeterRegistry meterRegistry,
                                                TelemetryConfig.MetricsConfig config,
                                                Producer<?, ?> producer,
                                                Properties driverProperties,
                                                MicrometerKafkaProducerTagsProvider tagsProvider) {
        this.micrometerMetrics = new KafkaClientMetrics(producer);
        this.tagsProvider = tagsProvider;
        this.micrometerMetrics.bindTo(meterRegistry);
        this.driverProperties = driverProperties;
        this.config = config;
        this.meterRegistry = meterRegistry;
        var clientIdObj = driverProperties.get(ProducerConfig.CLIENT_ID_CONFIG);
        this.clientId = (clientIdObj instanceof String s) ? s : null;
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
    public void sendEnd(ProducerRecord<?, ?> record, long durationNanos, Throwable e) {
        var key = new RecordDurationKey(record.topic(), Objects.requireNonNullElse(record.partition(), -1), e.getClass());
        var m = this.metrics.computeIfAbsent(key, this::metrics);
        m.record((double) durationNanos / 1_000_000);
    }

    @Override
    public void sendEnd(ProducerRecord<?, ?> record, long durationNanos, RecordMetadata metadata) {
        var key = new RecordDurationKey(record.topic(), Objects.requireNonNullElse(record.partition(), -1), null);
        var m = this.metrics.computeIfAbsent(key, this::metrics);
        m.record((double) durationNanos / 1_000_000);
    }

    @Override
    public void close() {
        this.micrometerMetrics.close();
        for (var i = this.metrics.entrySet().iterator(); i.hasNext(); ) {
            var entry = i.next();
            i.remove();
            try {
                entry.getValue().close();
            } catch (Throwable ignore) {
                // ignore
            }
        }
    }

    private DistributionSummary metrics(RecordDurationKey key) {
        var builder = DistributionSummary.builder("messaging.publish.duration")
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V120))
            .baseUnit("milliseconds")
            .tags(tagsProvider.getTopicPartitionTags(this.clientId, this.driverProperties, key));

        return builder.register(this.meterRegistry);
    }
}
