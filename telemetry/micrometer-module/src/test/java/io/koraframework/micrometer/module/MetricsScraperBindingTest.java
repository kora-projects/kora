package io.koraframework.micrometer.module;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The metrics endpoint resolves a {@code MetricsScraper} from the graph. {@link PrometheusMeterRegistryWrapper}
 * implements it, but the registry factory declares {@code Wrapped<MeterRegistry>}, so nothing was ever bound
 * under that type and every application answered the endpoint with "Metric Scraper disabled".
 */
class MetricsScraperBindingTest {

    private final MetricsModule module = new MetricsModule() {};

    @Test
    void aPrometheusRegistryIsScrapedInPrometheusFormat() throws Exception {
        var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        Counter.builder("kora.test.counter").register(registry).increment();

        var out = new ByteArrayOutputStream();
        module.prometheusMetricsScraper(registry).scrape(out);

        assertThat(out.toString(StandardCharsets.UTF_8)).contains("kora_test_counter");
    }

    @Test
    void anotherRegistryIsNotScrapedButDoesNotBreakTheEndpoint() {
        MeterRegistry registry = new SimpleMeterRegistry();

        var out = new ByteArrayOutputStream();
        assertThatCode(() -> module.prometheusMetricsScraper(registry).scrape(out)).doesNotThrowAnyException();
        assertThat(out.size()).isZero();
    }
}
