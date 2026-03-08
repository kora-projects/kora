package io.koraframework.resilient.retry;

public interface RetryManager {

    Retry get(String name);
}
