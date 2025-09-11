package ru.tinkoff.kora.micrometer.module.kafka.producer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import jakarta.annotation.Nullable;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerMetrics;
import ru.tinkoff.kora.micrometer.module.kafka.producer.tag.MicrometerKafkaProducerTagsProvider;
import ru.tinkoff.kora.micrometer.module.kafka.producer.tag.RecordDurationKey;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @see <a href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/messaging/messaging-metrics.md">messaging-metrics</a>
 */
public class OpentelemetryKafkaProducerMetrics implements KafkaProducerMetrics, AutoCloseable {

    private final ConcurrentHashMap<RecordDurationKey, DestinationMetrics> metrics = new ConcurrentHashMap<>();

    private final KafkaClientMetrics micrometerMetrics;
    private final Properties driverProperties;
    private final TelemetryConfig.MetricsConfig config;
    private final MeterRegistry meterRegistry;
    private final MicrometerKafkaProducerTagsProvider tagsProvider;
    @Nullable
    private final String clientId;

    public OpentelemetryKafkaProducerMetrics(MeterRegistry meterRegistry,
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

    record DestinationMetrics(Timer duration, Counter messages) {}

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
        m.messages.increment();
        m.duration.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void sendEnd(ProducerRecord<?, ?> record, long durationNanos, RecordMetadata metadata) {
        var key = new RecordDurationKey(record.topic(), Objects.requireNonNullElse(record.partition(), -1), null);
        var m = this.metrics.computeIfAbsent(key, this::metrics);
        m.messages.increment();
        m.duration.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void close() {
        this.micrometerMetrics.close();
        for (var i = this.metrics.entrySet().iterator(); i.hasNext(); ) {
            var entry = i.next();
            i.remove();
            try {
                entry.getValue().duration().close();
            } catch (Throwable ignore) {
                // ignore
            }
            try {
                entry.getValue().messages().close();
            } catch (Throwable ignore) {
                // ignore
            }
        }
    }

    private DestinationMetrics metrics(RecordDurationKey key) {
        var timer = Timer.builder("messaging.client.operation.duration")
            .serviceLevelObjectives(this.config.slo())
            .tags(tagsProvider.getTopicPartitionTags(this.clientId, this.driverProperties, key))
            .tag(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE.getKey(), MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues.SEND);

        var counter = Counter.builder("messaging.client.sent.messages")
            .tags(tagsProvider.getTopicPartitionTags(this.clientId, this.driverProperties, key));

        return new DestinationMetrics(timer.register(this.meterRegistry), counter.register(this.meterRegistry));
    }
}
