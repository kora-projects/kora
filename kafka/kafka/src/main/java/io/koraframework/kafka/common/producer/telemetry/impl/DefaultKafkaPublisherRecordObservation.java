package io.koraframework.kafka.common.producer.telemetry.impl;

import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContextStorage;
import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherRecordObservation;
import io.koraframework.kafka.common.producer.telemetry.impl.DefaultKafkaPublisherMetricsFactory.DefaultKafkaPublisherMetrics;
import io.koraframework.kafka.common.producer.telemetry.impl.DefaultKafkaPublisherTelemetry.TelemetryContext;
import io.koraframework.logging.common.MDC;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.jspecify.annotations.Nullable;
import io.koraframework.common.context.KoraContext;

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

public class DefaultKafkaPublisherRecordObservation implements KafkaPublisherRecordObservation {

    protected final long startedRecordSend = System.nanoTime();

    protected final TelemetryContext context;
    protected final DefaultKafkaPublisherLoggerFactory.DefaultKafkaPublisherLogger logger;
    protected final DefaultKafkaPublisherMetrics metrics;
    protected final String topic;
    protected final Span span;
    protected final MDC mdc;

    @Nullable
    protected Object key;
    protected Object value;
    @Nullable
    protected ProducerRecord<byte[], byte[]> record;
    @Nullable
    protected RecordMetadata metadata;
    @Nullable
    protected Throwable error;

    public DefaultKafkaPublisherRecordObservation(TelemetryContext context,
                                                  DefaultKafkaPublisherLoggerFactory.DefaultKafkaPublisherLogger logger,
                                                  DefaultKafkaPublisherMetrics metrics,
                                                  String topic,
                                                  Span span) {
        this.context = context;
        this.logger = logger;
        this.metrics = metrics;
        this.topic = topic;
        this.span = span;

        KoraContext current = KoraContext.current();
        this.mdc = (current != null && current.get(MDC.MDC_KEY) != null) ? current.get(MDC.MDC_KEY).fork() : new MDC();
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void observeData(Object key, Object value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public void observeRecord(ProducerRecord<byte[], byte[]> record) {
        this.record = record;
        this.logger.logRecordStart(record);
        W3CTraceContextPropagator.getInstance().inject(Context.root().with(span), record, ProducerRecordTextMapSetter.INSTANCE);
    }

    @Override
    public void onCompletion(RecordMetadata metadata, Exception exception) {
        KoraContext current = KoraContext.current();
        KoraContext baseContext = current != null ? current : KoraContext.empty();

        KoraContext updatedContext = Observation.attach(baseContext, this)
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, span.storeInContext(Context.root()))
            .with(MDC.MDC_KEY, this.mdc);

        KoraContext.runWith(updatedContext, new CompletionRunner(this, metadata, exception));
    }

    @Override
    public void observeError(Throwable e) {
        this.error = e;
        this.span.recordException(e);
        this.span.setStatus(StatusCode.ERROR);
    }

    @Override
    public void end() {
        this.metrics.reportHandleRecordTook(topic, key, value, record, metadata, error, startedRecordSend);
        this.logger.logRecordEnd(topic, metadata, error);

        this.span.end();
    }

    private enum ProducerRecordTextMapSetter implements TextMapSetter<ProducerRecord<?, ?>> {
        INSTANCE;

        @Override
        public void set(ProducerRecord<?, ?> carrier, String key, String value) {
            carrier.headers().add(key, value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final class CompletionRunner implements Supplier<Void> {
        private final DefaultKafkaPublisherRecordObservation observation;
        private final RecordMetadata metadata;
        private final Exception exception;

        public CompletionRunner(DefaultKafkaPublisherRecordObservation observation, RecordMetadata metadata, Exception exception) {
            this.observation = observation;
            this.metadata = metadata;
            this.exception = exception;
        }

        @Override
        public Void get() {
            if (exception != null) {
                observation.observeError(exception);
            } else {
                observation.metadata = metadata;
                var span = observation.span;
                span.setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID, String.valueOf(metadata.partition()));
                span.setAttribute(MessagingIncubatingAttributes.MESSAGING_KAFKA_OFFSET, metadata.offset());
                span.setStatus(StatusCode.OK);
            }
            observation.end();
            return null;
        }
    }
}
