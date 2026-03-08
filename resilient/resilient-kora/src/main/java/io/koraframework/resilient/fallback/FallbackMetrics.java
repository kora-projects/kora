package io.koraframework.resilient.fallback;

public interface FallbackMetrics {

    void recordExecute(String name, Throwable throwable);
}
