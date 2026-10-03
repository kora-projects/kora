package io.koraframework.nats.common.consumer;

import io.koraframework.application.graph.All;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Tag;
import io.koraframework.logging.common.masking.MaskingStrategy;
import io.koraframework.logging.common.masking.raw.DataMasker;
import io.koraframework.nats.common.consumer.deserializer.NatsDeserializersModule;
import io.koraframework.nats.common.consumer.telemetry.NatsConsumerTelemetry;
import io.koraframework.nats.common.consumer.telemetry.NatsConsumerTelemetryFactory;
import io.koraframework.nats.common.consumer.telemetry.impl.DefaultNatsConsumerBodyConverter;
import io.koraframework.nats.common.consumer.telemetry.impl.DefaultNatsConsumerLoggerFactory;
import io.koraframework.nats.common.consumer.telemetry.impl.DefaultNatsConsumerMetricsFactory;
import io.koraframework.nats.common.consumer.telemetry.impl.DefaultNatsConsumerTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

import java.util.stream.StreamSupport;

public interface NatsListenerModule extends NatsDeserializersModule {
    @DefaultComponent
    @Tag(NatsConsumerTelemetry.class)
    default MaskingStrategy natsConsumerMaskingStrategy() {
        return value -> "***";
    }

    @DefaultComponent
    default DefaultNatsConsumerBodyConverter natsConsumerBodyConverter(@Tag(NatsConsumerTelemetry.class) All<DataMasker> dataMaskers) {
        return new DefaultNatsConsumerBodyConverter(StreamSupport.stream(dataMaskers.spliterator(), false).toList());
    }

    @DefaultComponent
    default DefaultNatsConsumerLoggerFactory natsConsumerLoggerFactory(@Tag(NatsConsumerTelemetry.class) MaskingStrategy masking,
                                                                       DefaultNatsConsumerBodyConverter bodyConverter) {
        return new DefaultNatsConsumerLoggerFactory(masking, bodyConverter);
    }

    @DefaultComponent
    default NatsConsumerTelemetryFactory natsConsumerTelemetryFactory(@Nullable Tracer tracer, @Nullable MeterRegistry registry,
                                                                      @Nullable DefaultNatsConsumerLoggerFactory loggerFactory, @Nullable DefaultNatsConsumerMetricsFactory metricsFactory) {
        return new DefaultNatsConsumerTelemetryFactory(tracer, registry, loggerFactory, metricsFactory);
    }
}
