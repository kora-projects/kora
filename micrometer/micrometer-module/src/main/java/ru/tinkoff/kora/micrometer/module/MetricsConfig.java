package ru.tinkoff.kora.micrometer.module;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

@ConfigValueExtractor
public interface MetricsConfig {
    double[] DEFAULT_SLO = new double[]{1, 10, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 30000, 60000, 90000};

    DbMetricsConfig db();

    GrpcServerMetricsConfig grpcServer();

    HttpServerMetricsConfig httpServer();

    HttpClientMetricsConfig httpClient();

    SoapClientMetricsConfig soapClient();

    JmsConsumerMetricsConfig jmsConsumer();

    KafkaConsumerMetricsConfig kafkaConsumer();

    SchedulingMetricsConfig scheduling();

    @ConfigValueExtractor
    interface DbMetricsConfig {
        default double[] slo() {
            return DEFAULT_SLO;
        }
    }

    @ConfigValueExtractor
    interface GrpcServerMetricsConfig {
        default double[] slo() {
            return DEFAULT_SLO;
        }
    }

    @ConfigValueExtractor
    interface HttpServerMetricsConfig {
        default double[] slo() {
            return DEFAULT_SLO;
        }
    }

    @ConfigValueExtractor
    interface HttpClientMetricsConfig {
        default double[] slo() {
            return DEFAULT_SLO;
        }
    }

    @ConfigValueExtractor
    interface SoapClientMetricsConfig {
        default double[] slo() {
            return DEFAULT_SLO;
        }
    }

    @ConfigValueExtractor
    interface JmsConsumerMetricsConfig {
        default double[] slo() {
            return DEFAULT_SLO;
        }
    }

    @ConfigValueExtractor
    interface KafkaConsumerMetricsConfig {
        default double[] slo() {
            return DEFAULT_SLO;
        }
    }

    @ConfigValueExtractor
    interface SchedulingMetricsConfig {
        default double[] slo() {
            return DEFAULT_SLO;
        }
    }
}
