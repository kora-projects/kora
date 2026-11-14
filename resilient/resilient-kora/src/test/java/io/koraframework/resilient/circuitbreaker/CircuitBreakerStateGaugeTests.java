package io.koraframework.resilient.circuitbreaker;

import io.koraframework.resilient.circuitbreaker.telemetry.$CircuitBreakerTelemetryConfig_CircuitBreakerLoggingConfig_ConfigValueMapper;
import io.koraframework.resilient.circuitbreaker.telemetry.$CircuitBreakerTelemetryConfig_CircuitBreakerTracingConfig_ConfigValueMapper;
import io.koraframework.resilient.circuitbreaker.telemetry.$CircuitBreakerTelemetryConfig_ConfigValueMapper;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerTelemetryConfig;
import io.koraframework.resilient.circuitbreaker.telemetry.impl.DefaultCircuitBreakerTelemetryFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

class CircuitBreakerStateGaugeTests extends Assertions {

    @Test
    void stateGaugeReportsCircuitBreakerRecreatedByConfigRefresh() {
        var registry = new SimpleMeterRegistry();
        var telemetryFactory = new DefaultCircuitBreakerTelemetryFactory(null, registry, null, null);
        var telemetryConfig = telemetryConfig();
        var config = config();

        var circuitBreaker = new FixedWindowKoraCircuitBreaker("cb", config, _ -> true, telemetryFactory.get("cb", telemetryConfig));
        open(circuitBreaker);
        assertEquals(2.0, registry.get("resilient.circuitbreaker.state").gauge().value());

        // config refresh: the circuit breaker is recreated with new telemetry, the old one is OPEN
        var recreated = new FixedWindowKoraCircuitBreaker("cb", config, _ -> true, telemetryFactory.get("cb", telemetryConfig));
        open(recreated);
        assertTrue(recreated.tryAcquire()); // OPEN -> HALF_OPEN
        recreated.releaseOnSuccess(); // HALF_OPEN -> CLOSED
        assertEquals(CircuitBreaker.State.CLOSED, recreated.getState());
        assertEquals(0.0, registry.get("resilient.circuitbreaker.state").gauge().value(), "state of the recreated circuit breaker");
    }

    @Test
    void stateGaugeReportsClosedCircuitBreakerRecreatedByConfigRefreshBeforeAnyTransition() {
        var registry = new SimpleMeterRegistry();
        var telemetryFactory = new DefaultCircuitBreakerTelemetryFactory(null, registry, null, null);
        var config = config();

        var circuitBreaker = new FixedWindowKoraCircuitBreaker("cb", config, _ -> true, telemetryFactory.get("cb", telemetryConfig()));
        assertEquals(0.0, registry.get("resilient.circuitbreaker.state").gauge().value(), "gauge is registered at creation");
        open(circuitBreaker);
        assertEquals(2.0, registry.get("resilient.circuitbreaker.state").gauge().value());

        // config refresh: the new circuit breaker is CLOSED and healthy, it never transitions
        var recreated = new FixedWindowKoraCircuitBreaker("cb", config, _ -> true, telemetryFactory.get("cb", telemetryConfig()));
        assertTrue(recreated.tryAcquire());
        recreated.releaseOnSuccess();
        assertEquals(CircuitBreaker.State.CLOSED, recreated.getState());
        assertEquals(0.0, registry.get("resilient.circuitbreaker.state").gauge().value(), "state of the recreated circuit breaker");
    }

    private static CircuitBreakerTelemetryConfig telemetryConfig() {
        return new $CircuitBreakerTelemetryConfig_ConfigValueMapper.CircuitBreakerTelemetryConfig_Impl(
            new $CircuitBreakerTelemetryConfig_CircuitBreakerLoggingConfig_ConfigValueMapper.CircuitBreakerLoggingConfig_Defaults(),
            new CircuitBreakerTelemetryConfig.CircuitBreakerMetricsConfig() {
                @Override
                public boolean enabled() {
                    return true;
                }
            },
            new $CircuitBreakerTelemetryConfig_CircuitBreakerTracingConfig_ConfigValueMapper.CircuitBreakerTracingConfig_Defaults());
    }

    private static CircuitBreakerConfig config() {
        return new $CircuitBreakerConfig_ConfigValueMapper.CircuitBreakerConfig_Impl(
            true, CircuitBreakerConfig.CircuitBreakerType.FIXED_WINDOW,
            new $CircuitBreakerConfig_CountBasedConfig_ConfigValueMapper.CountBasedConfig_Impl(1, null),
            null, 100, Duration.ZERO, 1, 1, null);
    }

    private static void open(FixedWindowKoraCircuitBreaker circuitBreaker) {
        assertTrue(circuitBreaker.tryAcquire());
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }
}
