package io.koraframework.micrometer.module;

import java.util.Map;

/**
 * Contributes common tags applied to every metric in the {@link io.micrometer.core.instrument.MeterRegistry}.
 * <p>
 * Register any number of these as components; all of them are injected and their tags are merged together with
 * {@link MetricsConfig#tags()} into a global {@link io.micrometer.core.instrument.config.MeterFilter} that adds the
 * tags to all meters. On a key conflict the value from {@link MetricsConfig#tags()} wins.
 */
public interface MetricsTagsProvider {

    Map<String, String> tags();
}
