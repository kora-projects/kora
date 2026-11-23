package io.koraframework.nats.common;

import io.koraframework.nats.common.consumer.NatsListenerConfig;
import io.koraframework.nats.common.consumer.telemetry.NatsConsumerTelemetry;
import io.koraframework.nats.common.consumer.telemetry.NatsConsumerTelemetryConfig;
import io.koraframework.nats.common.consumer.telemetry.impl.NoopNatsConsumerTelemetry;
import io.koraframework.nats.common.producer.NatsPublisherConfig;
import io.koraframework.nats.common.producer.telemetry.NatsPublisherTelemetry;
import io.koraframework.nats.common.producer.telemetry.NatsPublisherTelemetryConfig;
import io.koraframework.nats.common.producer.telemetry.impl.NoopNatsPublisherTelemetry;
import io.nats.client.Options;

import java.time.Duration;
import java.util.Properties;

import static org.mockito.Mockito.*;

final class TestSupport {
    static final NatsPublisherTelemetry PUBLISHER_TELEMETRY = NoopNatsPublisherTelemetry.INSTANCE;
    static final NatsConsumerTelemetry CONSUMER_TELEMETRY = NoopNatsConsumerTelemetry.INSTANCE;

    static NatsPublisherTelemetryConfig publisherTelemetry(boolean tracing, boolean metrics) {
        return new NatsPublisherTelemetryConfig() {
            public NatsPublisherLoggingConfig logging() {
                return new NatsPublisherLoggingConfig() {
                    public boolean enabled() {
                        return false;
                    }
                };
            }

            public NatsPublisherMetricsConfig metrics() {
                return new NatsPublisherMetricsConfig() {
                    public boolean enabled() {
                        return metrics;
                    }
                };
            }

            public NatsPublisherTracingConfig tracing() {
                return new NatsPublisherTracingConfig() {
                    public boolean enabled() {
                        return tracing;
                    }
                };
            }
        };
    }

    static NatsConsumerTelemetryConfig consumerTelemetry(boolean tracing, boolean metrics) {
        return new NatsConsumerTelemetryConfig() {
            public NatsConsumerLoggingConfig logging() {
                return new NatsConsumerLoggingConfig() {
                    public boolean enabled() {
                        return false;
                    }
                };
            }

            public NatsConsumerMetricsConfig metrics() {
                return new NatsConsumerMetricsConfig() {
                    public boolean enabled() {
                        return metrics;
                    }
                };
            }

            public NatsConsumerTracingConfig tracing() {
                return new NatsConsumerTracingConfig() {
                    public boolean enabled() {
                        return tracing;
                    }
                };
            }
        };
    }

    static NatsClient client(String url) {
        return new NatsClient(new NatsConnectionConfig() {
            public boolean readinessProbe() {
                return true;
            }

            public Properties driverProperties() {
                var properties = new Properties();
                properties.put(Options.PROP_URL, url);
                return properties;
            }
        }, options -> options.connectionTimeout(Duration.ofSeconds(2)).reconnectWait(Duration.ofMillis(100)));
    }

    static NatsListenerConfig listener(String subject, String queue, NatsListenerConfig.JetStreamConfig js) {
        var config = mock(NatsListenerConfig.class, CALLS_REAL_METHODS);
        when(config.subjects()).thenReturn(subject == null ? java.util.List.of() : java.util.List.of(subject));
        when(config.queue()).thenReturn(queue);
        when(config.jetStream()).thenReturn(js);
        when(config.telemetry()).thenReturn(consumerTelemetry(false, false));
        when(config.pollTimeout()).thenReturn(Duration.ofMillis(200));
        when(config.shutdownWait()).thenReturn(Duration.ofSeconds(5));
        when(config.initializationFailTimeout()).thenReturn(Duration.ofSeconds(5));
        when(config.readinessProbe()).thenReturn(true);
        return config;
    }

    static NatsListenerConfig.JetStreamConfig jetStream(String stream, String durable) {
        var config = mock(NatsListenerConfig.JetStreamConfig.class, CALLS_REAL_METHODS);
        when(config.stream()).thenReturn(stream);
        when(config.durable()).thenReturn(durable);
        when(config.nakDelay()).thenReturn(Duration.ofMillis(100));
        return config;
    }

    static NatsPublisherConfig publisher(NatsPublisherConfig.Mode mode) {
        return new NatsPublisherConfig() {
            public Mode mode() {
                return mode;
            }

            public NatsPublisherTelemetryConfig telemetry() {
                return publisherTelemetry(false, false);
            }
        };
    }
}
