package io.koraframework.resilient.timeout;

public interface TimeoutMetrics {

    void recordTimeout(String name, long timeoutInNanos);
}
