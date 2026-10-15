package io.koraframework.resilient.bulkhead;

import static org.junit.jupiter.api.Assertions.*;

import io.koraframework.resilient.bulkhead.telemetry.BulkheadTelemetryConfig;
import io.koraframework.resilient.bulkhead.telemetry.impl.DefaultBulkheadTelemetryFactory;
import io.koraframework.resilient.bulkhead.telemetry.impl.NoopBulkheadTelemetry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BulkheadTelemetryTests {

    private static BulkheadTelemetryConfig telemetryConfig(boolean enabled) {
        return new BulkheadTelemetryConfig() {

            @Override
            public BulkheadLoggingConfig logging() {
                return new BulkheadLoggingConfig() {

                    @Override
                    public boolean enabled() {
                        return false;
                    }
                };
            }

            @Override
            public BulkheadMetricsConfig metrics() {
                return new BulkheadMetricsConfig() {

                    @Override
                    public boolean enabled() {
                        return enabled;
                    }

                    @Override
                    public Map<String, String> tags() {
                        return Map.of("service", "test");
                    }
                };
            }

            @Override
            public BulkheadTracingConfig tracing() {
                return new BulkheadTracingConfig() {};
            }
        };
    }

    @Test
    void adaptiveLimitQueueAndWaitMetricsReflectAdmissionState() {
        var registry = new SimpleMeterRegistry();
        var config = new BulkheadModesTests.Config(
            4, 1, java.time.Duration.ofSeconds(10), BulkheadConfig.Type.AIMD, BulkheadModesTests.adaptive(2)
        );
        var clock = new java.util.concurrent.atomic.AtomicLong();
        var factory = new DefaultBulkheadTelemetryFactory(null, registry, null, null);
        var bulkhead = new KoraBulkhead("orders", config, factory.get("orders", telemetryConfig(true)), clock::get);
        try {
            var first = bulkhead.acquire();
            var second = bulkhead.acquire();
            var queued = bulkhead.acquireAsync().toCompletableFuture();
            assertEquals(2, registry.get("resilient.bulkhead.limit").gauge().value());
            assertEquals(4, registry.get("resilient.bulkhead.max_limit").gauge().value());
            assertEquals(1, registry.get("resilient.bulkhead.queue_length").gauge().value());
            clock.addAndGet(java.time.Duration.ofMillis(10).toNanos());
            second.close();
            assertEquals(3, registry.get("resilient.bulkhead.limit").gauge().value());
            assertEquals(0, registry.get("resilient.bulkhead.queue_length").gauge().value());
            assertEquals(1, registry.get("resilient.bulkhead.queue_wait").timer().count());
            assertEquals(
                0.01,
                registry.get("resilient.bulkhead.queue_wait").timer().totalTime(java.util.concurrent.TimeUnit.SECONDS),
                0.0001
            );
            first.close();
            queued.join().close();
            assertEquals(0, registry.get("resilient.bulkhead.in_flight").gauge().value());
            assertEquals(3, registry.get("resilient.bulkhead.duration").timer().count());
        } finally {
            bulkhead.release();
            registry.close();
        }
    }

    @Test
    void metricsRecordAdmissionImmediatelyAndDurationOnRelease() {
        var registry = new SimpleMeterRegistry();
        try {
            var factory = new DefaultBulkheadTelemetryFactory(null, registry, null, null);
            var bulkhead = new KoraBulkhead(
                "orders",
                new $BulkheadConfig_ConfigValueMapper.BulkheadConfig_Impl(
                    true, 1, null, BulkheadConfig.Type.FIXED, 0, java.time.Duration.ZERO, null
                ), factory.get("orders", telemetryConfig(true))
            );
            var permit = bulkhead.acquire();
            assertEquals(1, registry.get("resilient.bulkhead.in_flight").tag("service", "test").gauge().value());
            assertEquals(1, registry.get("resilient.bulkhead.limit").gauge().value());
            assertEquals(1, registry.get("resilient.bulkhead.utilization").gauge().value());
            assertEquals(1, registry.get("resilient.bulkhead.saturated").gauge().value());
            assertEquals(1, registry.get("resilient.bulkhead.acquire").tag("resilient.status", "acquired").counter().count());
            assertNull(bulkhead.tryAcquire());
            assertEquals(1, registry.get("resilient.bulkhead.acquire").tag("resilient.status", "rejected").counter().count());
            permit.close();
            permit.close();
            assertEquals(0, registry.get("resilient.bulkhead.in_flight").gauge().value());
            assertEquals(0, registry.get("resilient.bulkhead.saturated").gauge().value());
            assertEquals(1, registry.get("resilient.bulkhead.duration").timer().count());
        } finally {
            registry.close();
        }
    }

    @Test
    void refreshedLimiterRebindsStateGauges() {
        var registry = new SimpleMeterRegistry();
        try {
            var factory = new DefaultBulkheadTelemetryFactory(null, registry, null, null);
            var first = new KoraBulkhead(
                "orders",
                new $BulkheadConfig_ConfigValueMapper.BulkheadConfig_Impl(
                    true, 1, null, BulkheadConfig.Type.FIXED, 0, java.time.Duration.ZERO, null
                ), factory.get("orders", telemetryConfig(true))
            );
            try (var oldPermit = first.acquire()) {
                var refreshed = new KoraBulkhead(
                    "orders",
                    new $BulkheadConfig_ConfigValueMapper.BulkheadConfig_Impl(
                        true, 2, null, BulkheadConfig.Type.FIXED, 0, java.time.Duration.ZERO, null
                    ), factory.get("orders", telemetryConfig(true))
                );
                assertEquals(2, registry.get("resilient.bulkhead.limit").gauge().value());
                assertEquals(0, registry.get("resilient.bulkhead.in_flight").gauge().value());
                try (var newPermit = refreshed.acquire()) {
                    assertEquals(1, registry.get("resilient.bulkhead.in_flight").gauge().value());
                    assertEquals(0.5, registry.get("resilient.bulkhead.utilization").gauge().value());
                }
            }
        } finally {
            registry.close();
        }
    }

    @Test
    void disabledTelemetryRegistersNoMetrics() {
        var registry = new SimpleMeterRegistry();
        try {
            var factory = new DefaultBulkheadTelemetryFactory(null, registry, null, null);
            var telemetry = factory.get("orders", telemetryConfig(false));
            assertSame(NoopBulkheadTelemetry.INSTANCE, telemetry);
            var bulkhead = new KoraBulkhead(
                "orders",
                new $BulkheadConfig_ConfigValueMapper.BulkheadConfig_Impl(
                    true, 1, null, BulkheadConfig.Type.FIXED, 0, java.time.Duration.ZERO, null
                ), telemetry
            );
            assertEquals("ok", bulkhead.execute(() -> "ok"));
            assertTrue(registry.getMeters().isEmpty());
        } finally {
            registry.close();
        }
    }
}
