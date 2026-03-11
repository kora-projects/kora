package io.koraframework.resilient.timeout;

final class NoopTimeoutMetrics implements TimeoutMetrics {

    @Override
    public void recordTimeout(String name, long timeoutInNanos) {
        // do nothing
    }
}
