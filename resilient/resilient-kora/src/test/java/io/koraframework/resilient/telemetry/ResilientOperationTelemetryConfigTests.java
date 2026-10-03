package io.koraframework.resilient.telemetry;

import static org.junit.jupiter.api.Assertions.*;

import io.koraframework.resilient.bulkhead.BulkheadConfig;
import io.koraframework.resilient.bulkhead.telemetry.BulkheadOperationTelemetryConfig;
import io.koraframework.resilient.bulkhead.telemetry.BulkheadTelemetryConfig;
import io.koraframework.resilient.circuitbreaker.CircuitBreakerConfig;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerOperationTelemetryConfig;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerTelemetryConfig;
import io.koraframework.resilient.ratelimiter.RateLimiterConfig;
import io.koraframework.resilient.ratelimiter.telemetry.RateLimiterOperationTelemetryConfig;
import io.koraframework.resilient.ratelimiter.telemetry.RateLimiterTelemetryConfig;
import io.koraframework.resilient.retry.RetryConfig;
import io.koraframework.resilient.retry.telemetry.RetryOperationTelemetryConfig;
import io.koraframework.resilient.retry.telemetry.RetryTelemetryConfig;
import io.koraframework.resilient.timeout.TimeoutConfig;
import io.koraframework.resilient.timeout.telemetry.TimeoutOperationTelemetryConfig;
import io.koraframework.resilient.timeout.telemetry.TimeoutTelemetryConfig;
import io.koraframework.telemetry.common.TelemetryConfig;
import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ResilientOperationTelemetryConfigTests {

    private static final Duration[] GLOBAL_SLO = {
            Duration.ofMillis(100)
    };
    private static final Map<String, String> GLOBAL_TAGS = Map.of("global", "true");
    private static final Map<String, String> OPERATION_TAGS = Map.of("operation", "true");
    private static final Map<String, String> GLOBAL_ATTRIBUTES = Map.of("globalAttr", "true");

    @Test
    void circuitBreakerTelemetryMergesOperationPropertiesOverGlobalProperties() {
        assertMerged(new CircuitBreakerOperationTelemetryConfig(global(), operation()));
    }

    @Test
    void retryTelemetryMergesOperationPropertiesOverGlobalProperties() {
        assertMerged(new RetryOperationTelemetryConfig(global(), operation()));
    }

    @Test
    void timeoutTelemetryMergesOperationPropertiesOverGlobalProperties() {
        assertMerged(new TimeoutOperationTelemetryConfig(global(), operation()));
    }

    @Test
    void rateLimiterTelemetryMergesOperationPropertiesOverGlobalProperties() {
        assertMerged(new RateLimiterOperationTelemetryConfig(global(), operation()));
    }

    @Test
    void bulkheadTelemetryMergesOperationPropertiesOverGlobalProperties() {
        assertMerged(new BulkheadOperationTelemetryConfig(global(), operation()));
    }

    @Test
    void bulkheadTelemetryInheritsGlobalWhenOperationOverrideIsAbsent() {
        var telemetry = new BulkheadOperationTelemetryConfig(global(), null);
        assertTrue(telemetry.logging().enabled());
        assertTrue(telemetry.metrics().enabled());
        assertArrayEquals(GLOBAL_SLO, telemetry.metrics().slo());
        assertEquals(GLOBAL_TAGS, telemetry.metrics().tags());
        assertFalse(telemetry.tracing().enabled());
    }

    private static void assertMerged(TelemetryConfig telemetry) {
        assertFalse(telemetry.logging().enabled());
        assertTrue(telemetry.metrics().enabled());
        assertArrayEquals(GLOBAL_SLO, telemetry.metrics().slo());
        assertEquals(OPERATION_TAGS, telemetry.metrics().tags());
        assertTrue(telemetry.tracing().enabled());
        assertEquals(GLOBAL_ATTRIBUTES, telemetry.tracing().attributes());
    }

    private static GlobalTelemetryConfig global() {
        return new GlobalTelemetryConfig();
    }

    private static OperationTelemetryConfig operation() {
        return new OperationTelemetryConfig();
    }

    private static final class GlobalTelemetryConfig
            implements CircuitBreakerTelemetryConfig, RetryTelemetryConfig, TimeoutTelemetryConfig, RateLimiterTelemetryConfig,
            BulkheadTelemetryConfig {

        @Override
        public GlobalLoggingConfig logging() {
            return new GlobalLoggingConfig();
        }

        @Override
        public GlobalMetricsConfig metrics() {
            return new GlobalMetricsConfig();
        }

        @Override
        public GlobalTracingConfig tracing() {
            return new GlobalTracingConfig();
        }
    }

    private static final class GlobalLoggingConfig
            implements CircuitBreakerTelemetryConfig.CircuitBreakerLoggingConfig, RetryTelemetryConfig.RetryLoggingConfig,
            TimeoutTelemetryConfig.TimeoutLoggingConfig, RateLimiterTelemetryConfig.RateLimiterLoggingConfig,
            BulkheadTelemetryConfig.BulkheadLoggingConfig {

        @Override
        public boolean enabled() {
            return true;
        }
    }

    private static final class GlobalMetricsConfig
            implements CircuitBreakerTelemetryConfig.CircuitBreakerMetricsConfig, RetryTelemetryConfig.RetryMetricsConfig,
            TimeoutTelemetryConfig.TimeoutMetricsConfig, RateLimiterTelemetryConfig.RateLimiterMetricsConfig,
            BulkheadTelemetryConfig.BulkheadMetricsConfig {

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public Duration[] slo() {
            return GLOBAL_SLO;
        }

        @Override
        public Map<String, String> tags() {
            return GLOBAL_TAGS;
        }
    }

    private static final class GlobalTracingConfig
            implements CircuitBreakerTelemetryConfig.CircuitBreakerTracingConfig, RetryTelemetryConfig.RetryTracingConfig,
            TimeoutTelemetryConfig.TimeoutTracingConfig, RateLimiterTelemetryConfig.RateLimiterTracingConfig,
            BulkheadTelemetryConfig.BulkheadTracingConfig {

        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public Map<String, String> attributes() {
            return GLOBAL_ATTRIBUTES;
        }
    }

    private static final class OperationTelemetryConfig
            implements CircuitBreakerConfig.TelemetryConfig, RetryConfig.TelemetryConfig, TimeoutConfig.TelemetryConfig,
            RateLimiterConfig.TelemetryConfig, BulkheadConfig.TelemetryConfig {

        @Override
        public OperationLoggingConfig logging() {
            return new OperationLoggingConfig();
        }

        @Override
        public OperationMetricsConfig metrics() {
            return new OperationMetricsConfig();
        }

        @Override
        public OperationTracingConfig tracing() {
            return new OperationTracingConfig();
        }
    }

    private static final class OperationLoggingConfig
            implements CircuitBreakerConfig.TelemetryConfig.LoggingConfig, RetryConfig.TelemetryConfig.LoggingConfig,
            TimeoutConfig.TelemetryConfig.LoggingConfig, RateLimiterConfig.TelemetryConfig.LoggingConfig,
            BulkheadConfig.TelemetryConfig.LoggingConfig {

        @Override
        public @Nullable Boolean enabled() {
            return false;
        }
    }

    private static final class OperationMetricsConfig
            implements CircuitBreakerConfig.TelemetryConfig.MetricsConfig, RetryConfig.TelemetryConfig.MetricsConfig,
            TimeoutConfig.TelemetryConfig.MetricsConfig, RateLimiterConfig.TelemetryConfig.MetricsConfig,
            BulkheadConfig.TelemetryConfig.MetricsConfig {

        @Override
        public @Nullable Boolean enabled() {
            return null;
        }

        @Override
        public Duration @Nullable [] slo() {
            return null;
        }

        @Override
        public @Nullable Map<String, String> tags() {
            return OPERATION_TAGS;
        }
    }

    private static final class OperationTracingConfig
            implements CircuitBreakerConfig.TelemetryConfig.TracingConfig, RetryConfig.TelemetryConfig.TracingConfig,
            TimeoutConfig.TelemetryConfig.TracingConfig, RateLimiterConfig.TelemetryConfig.TracingConfig,
            BulkheadConfig.TelemetryConfig.TracingConfig {

        @Override
        public @Nullable Boolean enabled() {
            return true;
        }

        @Override
        public @Nullable Map<String, String> attributes() {
            return null;
        }
    }
}
