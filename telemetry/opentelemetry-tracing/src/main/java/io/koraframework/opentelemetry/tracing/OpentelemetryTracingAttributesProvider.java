package io.koraframework.opentelemetry.tracing;

import java.util.Map;

/**
 * Contributes attributes to the OpenTelemetry {@link io.opentelemetry.sdk.resources.Resource} that is attached to the
 * tracer provider. Every span produced by the provider is associated with that resource, so attributes returned here
 * apply to all spans.
 * <p>
 * Register any number of these as components; all of them are injected and their attributes are merged into the
 * resource together with {@link OpentelemetryTracingConfig#attributes()}. On a key conflict the value from
 * {@code OpentelemetryTracingConfig#attributes()} wins.
 */
public interface OpentelemetryTracingAttributesProvider {

    Map<String, String> attributes();
}
