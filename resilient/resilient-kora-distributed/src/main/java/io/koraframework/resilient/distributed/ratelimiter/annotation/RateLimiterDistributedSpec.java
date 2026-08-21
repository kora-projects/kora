package io.koraframework.resilient.distributed.ratelimiter.annotation;

import io.koraframework.resilient.distributed.ratelimiter.DistributedRateLimiterClient;
import io.koraframework.resilient.distributed.ratelimiter.KoraDistributedRateLimiter;
import io.koraframework.resilient.ratelimiter.RateLimiter;

import java.lang.annotation.*;

/**
 * Marks a {@link RateLimiter} interface whose implementation should be generated
 * as a <b>distributed</b> (Redis-backed) limiter rather than an in-JVM one.
 *
 * <p>The generated impl extends
 * {@link KoraDistributedRateLimiter}, so it is a drop-in
 * {@code RateLimiter} usable with {@code @RateLimited}. It requires a
 * {@link DistributedRateLimiterClient} in the graph, supplied by a
 * backend module such as {@code LettuceDistributedResilientModule}.
 */
@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.TYPE})
public @interface RateLimiterDistributedSpec {

    /**
     * @return path for the distributed RateLimiter config
     */
    String value();
}
