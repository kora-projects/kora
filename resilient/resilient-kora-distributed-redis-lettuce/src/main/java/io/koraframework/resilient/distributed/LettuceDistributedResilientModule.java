package io.koraframework.resilient.distributed;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Tag;
import io.koraframework.redis.lettuce.LettuceModule;
import io.koraframework.resilient.distributed.ratelimiter.DistributedRateLimiterClient;
import io.koraframework.resilient.distributed.ratelimiter.lettuce.LettuceDistributedRateLimiterClient;
import io.koraframework.resilient.distributed.retry.DistributedRetryBudgetClient;
import io.koraframework.resilient.distributed.retry.lettuce.LettuceDistributedRetryBudgetClient;
import io.lettuce.core.AbstractRedisClient;

/**
 * Lettuce/Redis backing for the distributed resilient components. Supplies the backend clients
 * leaves abstract — {@link DistributedRateLimiterClient} and {@link DistributedRetryBudgetClient} — reusing
 * the shared {@link AbstractRedisClient} provided by {@link LettuceModule}.
 *
 * <p>Include this module to get a working distributed rate limiter and retry budget on Redis.
 */
public interface LettuceDistributedResilientModule extends LettuceModule {

    @Tag(LettuceDistributedResilientModule.class)
    @DefaultComponent
    default AbstractRedisClient lettuceDistributedResilientRedisClient(AbstractRedisClient redisClient) {
        return redisClient;
    }

    @DefaultComponent
    default DistributedRateLimiterClient lettuceRateLimiterClient(@Tag(LettuceDistributedResilientModule.class) AbstractRedisClient redisClient) {
        return new LettuceDistributedRateLimiterClient(redisClient);
    }

    @DefaultComponent
    default DistributedRetryBudgetClient lettuceRetryBudgetClient(@Tag(LettuceDistributedResilientModule.class) AbstractRedisClient redisClient) {
        return new LettuceDistributedRetryBudgetClient(redisClient);
    }
}
