package ru.tinkoff.kora.micrometer.module;

import javax.annotation.Nullable;
import java.util.List;

public record MetricsConfig(
    @Nullable DbMetricsConfig db,
    @Nullable GrpcServerMetricsConfig grpcServer,
    @Nullable HttpServerMetricsConfig httpServer,
    @Nullable HttpClientMetricsConfig httpClient,
    @Nullable SoapClientMetricsConfig soapClient,
    @Nullable JmsConsumerMetricsConfig jmsConsumer,
    @Nullable KafkaConsumerMetricsConfig kafkaConsumer,
    @Nullable SchedulingMetricsConfig scheduling
) {
    public record DbMetricsConfig(@Nullable List<Double> slo) {}

    public record GrpcServerMetricsConfig(@Nullable List<Double> slo) {}

    public record HttpServerMetricsConfig(@Nullable List<Double> slo) {}

    public record HttpClientMetricsConfig(@Nullable List<Double> slo) {}

    public record SoapClientMetricsConfig(@Nullable List<Double> slo) {}

    public record JmsConsumerMetricsConfig(@Nullable List<Double> slo) {}

    public record KafkaConsumerMetricsConfig(@Nullable List<Double> slo) {}

    public record SchedulingMetricsConfig(@Nullable List<Double> slo) {}
}
