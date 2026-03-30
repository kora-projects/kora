package io.koraframework.resilient.annotation.processor.aop.testdata;

import io.koraframework.common.Component;
import io.koraframework.common.annotation.Root;
import io.koraframework.resilient.ratelimiter.annotation.RateLimit;

@Component
@Root
public class RateLimitTarget {

    @RateLimit("custom1")
    public String getValueSync() {
        return "OK";
    }

    @RateLimit("custom1")
    public void getValueSyncVoid() {
        // no-op
    }
}
