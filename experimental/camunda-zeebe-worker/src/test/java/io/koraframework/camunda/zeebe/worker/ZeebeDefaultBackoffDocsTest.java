package io.koraframework.camunda.zeebe.worker;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZeebeDefaultBackoffDocsTest {

    @Test
    void defaultBackoffNeverProducesNonPositiveDelay() {
        var supplier = new ZeebeWorkerModule() {}.zeebeWorkerBackoffFactory()
            .build(ZeebeWorkerConfig.DEFAULT_BACKOFF_CONFIG);
        long min = Long.MAX_VALUE;
        long delay = 0;
        for (int i = 0; i < 100_000; i++) {
            delay = supplier.supplyRetryDelay(delay);
            min = Math.min(min, delay);
        }
        assertThat(min).as("retry delay with default backoff config").isPositive();
    }

    @Test
    void backoffFactoryRejectsJitterOutsideZeroToOne() {
        var factory = new ZeebeWorkerModule() {}.zeebeWorkerBackoffFactory();
        var config = new $ZeebeWorkerConfig_BackoffConfig_ConfigValueMapper.BackoffConfig_Impl(
            Duration.ofMillis(500), Duration.ofMillis(100), 1.0, 1.1
        );
        assertThatThrownBy(() -> factory.build(config))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("jitter");
    }
}
