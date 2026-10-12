package io.koraframework.cache.redis;

import io.koraframework.cache.redis.lettuce.LettuceClusterCacheClient;
import io.koraframework.cache.redis.lettuce.LettuceStandaloneCacheClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LettuceCacheClientFailedInitReleaseTest {

    private static final RedisURI UNREACHABLE = RedisURI.builder().withHost("127.0.0.1").withPort(1).withTimeout(Duration.ofSeconds(1)).build();

    @Test
    void standaloneReleaseAfterFailedInitDoesNotThrow() {
        var redisClient = RedisClient.create(UNREACHABLE);
        try {
            var client = new LettuceStandaloneCacheClient(redisClient, UNREACHABLE);
            assertThatThrownBy(client::init).isInstanceOf(IllegalStateException.class);
            // the graph releases a node whose init() failed
            assertThatCode(client::release).doesNotThrowAnyException();
            assertThatCode(client::release).doesNotThrowAnyException();
        } finally {
            redisClient.shutdown();
        }
    }

    @Test
    void clusterReleaseAfterFailedInitDoesNotThrow() {
        var redisClient = RedisClusterClient.create(UNREACHABLE);
        try {
            var client = new LettuceClusterCacheClient(redisClient);
            assertThatThrownBy(client::init).isInstanceOf(IllegalStateException.class);
            // the graph releases a node whose init() failed
            assertThatCode(client::release).doesNotThrowAnyException();
            assertThatCode(client::release).doesNotThrowAnyException();
        } finally {
            redisClient.shutdown();
        }
    }
}
