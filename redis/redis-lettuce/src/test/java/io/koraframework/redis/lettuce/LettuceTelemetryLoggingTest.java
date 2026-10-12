package io.koraframework.redis.lettuce;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.koraframework.redis.lettuce.telemetry.DefaultLettuceTelemetry;
import io.koraframework.redis.lettuce.telemetry.LettuceTelemetryConfig;
import io.koraframework.test.redis.RedisParams;
import io.koraframework.test.redis.RedisTestContainer;
import io.lettuce.core.RedisClient;
import io.lettuce.core.metrics.CommandLatencyRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RedisTestContainer
class LettuceTelemetryLoggingTest {

    static LettuceConfig config(String uri, boolean loggingEnabled, boolean metricsEnabled) {
        var logging = new LettuceTelemetryConfig.LettuceLoggingConfig() {
            @Override
            public boolean enabled() { return loggingEnabled; }
        };
        var metrics = new LettuceTelemetryConfig.LettuceMetricsConfig() {
            @Override
            public boolean enabled() { return metricsEnabled; }
        };
        var telemetry = new LettuceTelemetryConfig() {
            @Override
            public LettuceLoggingConfig logging() { return logging; }

            @Override
            public LettuceMetricsConfig metrics() { return metrics; }
        };
        var ssl = new LettuceConfig.SslConfig() {};
        return new LettuceConfig() {
            @Override public String uri() { return uri; }
            @Override public Integer database() { return null; }
            @Override public String user() { return null; }
            @Override public String password() { return null; }
            @Override public LettuceTelemetryConfig telemetry() { return telemetry; }
            @Override public SslConfig ssl() { return ssl; }
        };
    }

    private static int failedCommandLogs(RedisParams redis, @Nullable MeterRegistry registry, boolean loggingEnabled, boolean metricsEnabled) {
        var logger = (Logger) LoggerFactory.getLogger(DefaultLettuceTelemetry.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        var previousLevel = logger.getLevel();
        logger.setLevel(Level.TRACE);
        try {
            var factory = new LettuceFactory(registry, null, null, null, null, null);
            var client = (RedisClient) factory.build(config(redis.uri().toString(), loggingEnabled, metricsEnabled));
            try (var connection = client.connect()) {
                var sync = connection.sync();
                sync.set("telemetry-key", "not-a-number");
                assertThatThrownBy(() -> sync.incr("telemetry-key"));
            } finally {
                client.shutdown();
            }
            return (int) appender.list.stream().filter(e -> e.getFormattedMessage().startsWith("Command execution failed")).count();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }
    }

    @Test
    void loggingAndMetricsEnabledLogsFailedCommand(RedisParams redis) {
        var registry = new SimpleMeterRegistry();
        assertThat(failedCommandLogs(redis, registry, true, true)).isPositive();
        assertThat(registry.find("db.client.operation.duration").timers()).isNotEmpty();
    }

    @Test
    void loggingEnabledWithMetricsDisabledLogsFailedCommand(RedisParams redis) {
        var registry = new SimpleMeterRegistry();
        assertThat(failedCommandLogs(redis, registry, true, false)).isPositive();
        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    void loggingEnabledWithoutMeterRegistryLogsFailedCommand(RedisParams redis) {
        assertThat(failedCommandLogs(redis, null, true, true)).isPositive();
    }

    @Test
    void loggingDisabledDoesNotLogFailedCommand(RedisParams redis) {
        assertThat(failedCommandLogs(redis, new SimpleMeterRegistry(), false, true)).isZero();
    }

    @Test
    void customRecorderIsUsedWithMetricsEnabledAndNoMeterRegistry(RedisParams redis) {
        CommandLatencyRecorder custom = (local, remote, command, firstResponse, completion) -> {};
        var factory = new LettuceFactory(null, custom, null, null, null, null);
        var client = (RedisClient) factory.build(config(redis.uri().toString(), false, true));
        try {
            assertThat(client.getResources().commandLatencyRecorder()).isSameAs(custom);
        } finally {
            client.shutdown();
        }
    }
}
