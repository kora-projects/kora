package io.koraframework.kafka.common.consumer;

import io.koraframework.application.graph.All;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Tag;
import io.koraframework.kafka.common.consumer.deserializer.KafkaDeserializersModule;
import io.koraframework.kafka.common.consumer.telemetry.KafkaConsumerTelemetryFactory;
import io.koraframework.kafka.common.consumer.telemetry.KafkaConsumerTelemetry;
import io.koraframework.kafka.common.consumer.telemetry.impl.DefaultKafkaConsumerBodyConverter;
import io.koraframework.kafka.common.consumer.telemetry.impl.DefaultKafkaConsumerLoggerFactory;
import io.koraframework.kafka.common.consumer.telemetry.impl.DefaultKafkaConsumerMetricsFactory;
import io.koraframework.kafka.common.consumer.telemetry.impl.DefaultKafkaConsumerTelemetryFactory;
import io.koraframework.kafka.common.utils.KafkaArgMaskingStrategy;
import io.koraframework.logging.common.masking.raw.DataMasker;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

public interface KafkaListenerModule extends KafkaDeserializersModule {

    @Tag(KafkaConsumerTelemetry.class)
    @DefaultComponent
    default KafkaArgMaskingStrategy defaultKafkaConsumerArgMaskingStrategy() {
        return (key, value) -> "***";
    }

    @DefaultComponent
    default DefaultKafkaConsumerBodyConverter defaultKafkaConsumerBodyConverter(@Tag(KafkaConsumerTelemetry.class) All<DataMasker> dataMaskers) {
        return new DefaultKafkaConsumerBodyConverter(StreamSupport.stream(dataMaskers.spliterator(), false).toList());
    }

    @DefaultComponent
    default DefaultKafkaConsumerLoggerFactory defaultKafkaConsumerLoggerFactory(@Tag(KafkaConsumerTelemetry.class) KafkaArgMaskingStrategy maskingStrategy,
                                                                                 DefaultKafkaConsumerBodyConverter bodyConverter) {
        return new DefaultKafkaConsumerLoggerFactory(maskingStrategy, bodyConverter);
    }

    @DefaultComponent
    default KafkaConsumerTelemetryFactory defaultKafkaConsumerTelemetryFactory(@Nullable Tracer tracer,
                                                                               @Nullable MeterRegistry meterRegistry,
                                                                               @Nullable DefaultKafkaConsumerLoggerFactory loggerFactory,
                                                                               @Nullable DefaultKafkaConsumerMetricsFactory metricsFactory) {
        return new DefaultKafkaConsumerTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }
}
