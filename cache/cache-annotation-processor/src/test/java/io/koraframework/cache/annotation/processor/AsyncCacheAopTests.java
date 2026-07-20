package io.koraframework.cache.annotation.processor;

import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.cache.caffeine.CaffeineCacheModule;
import io.koraframework.cache.redis.RedisCacheClient;
import io.koraframework.cache.redis.RedisCacheModule;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class AsyncCacheAopTests extends AbstractCacheAnnotationProcessorTests implements CaffeineCacheModule, RedisCacheModule {

    @Test
    public void cacheableAsyncWithRedis() {
        compileRedis("""
            @Cacheable(value = DummyCache.class, mode = CacheMode.ASYNC)
            public String getValue(String arg1) {
                return value;
            }
            """);

        var client = new BlockingRedisCacheClient();
        var cache = newRedisCache(client);
        var service = newObject("$CacheableSync__AopProxy", cache, Executors.newSingleThreadExecutor());
        client.blockSet();

        assertThat(call(service, "getValue", "1")).isEqualTo("1");
        client.awaitBlocked();
        assertThat(call(cache, "get", "1")).isNull();

        client.release();
        assertEventually(() -> "1".equals(call(cache, "get", "1")));
    }

    @Test
    public void cachePutAsyncWithRedis() {
        compileRedis("""
            @CachePut(value = DummyCache.class, args = {"arg1"}, mode = CacheMode.ASYNC)
            public String putValue(String arg1) {
                return value;
            }
            """);

        var client = new BlockingRedisCacheClient();
        var cache = newRedisCache(client);
        var service = newObject("$CacheableSync__AopProxy", cache, Executors.newSingleThreadExecutor());
        client.blockSet();

        assertThat(call(service, "putValue", "1")).isEqualTo("1");
        client.awaitBlocked();
        assertThat(call(cache, "get", "1")).isNull();

        client.release();
        assertEventually(() -> "1".equals(call(cache, "get", "1")));
    }

    @Test
    public void cacheInvalidateAsyncWithRedis() {
        compileRedis("""
            @CacheInvalidate(value = DummyCache.class, mode = CacheMode.ASYNC)
            public void evictValue(String arg1) {
            }
            """);

        var client = new BlockingRedisCacheClient();
        var cache = newRedisCache(client);
        call(cache, "put", "1", "1");
        var service = newObject("$CacheableSync__AopProxy", cache, Executors.newSingleThreadExecutor());
        client.blockDelOne();

        call(service, "evictValue", "1");
        client.awaitBlocked();
        assertThat(call(cache, "get", "1")).isEqualTo("1");

        client.release();
        assertEventually(() -> call(cache, "get", "1") == null);
    }

    @Test
    public void cacheInvalidateAllAsyncWithRedis() {
        compileRedis("""
            @CacheInvalidateAll(value = DummyCache.class, mode = CacheMode.ASYNC)
            public void evictAll() {
            }
            """);

        var client = new BlockingRedisCacheClient();
        var cache = newRedisCache(client);
        call(cache, "put", "1", "1");
        call(cache, "put", "2", "2");
        var service = newObject("$CacheableSync__AopProxy", cache, Executors.newSingleThreadExecutor());
        client.blockDelMany();

        call(service, "evictAll");
        client.awaitBlocked();
        assertThat(call(cache, "get", "1")).isEqualTo("1");

        client.release();
        assertEventually(() -> call(cache, "get", "1") == null);
        assertEventually(() -> call(cache, "get", "2") == null);
    }

    @Test
    public void cacheableAsyncWithCaffeineIsSync() {
        compileCaffeine("""
            @Cacheable(value = DummyCache.class, mode = CacheMode.ASYNC)
            public String getValue(String arg1) {
                return value;
            }
            """);

        var cache = newCaffeineCache();
        var service = newObject("$CacheableSync__AopProxy", cache);

        assertThat(call(service, "getValue", "1")).isEqualTo("1");

        assertThat(call(cache, "get", "1")).isEqualTo("1");
    }

    @Test
    public void cachePutAsyncWithCaffeineIsSync() {
        compileCaffeine("""
            @CachePut(value = DummyCache.class, args = {"arg1"}, mode = CacheMode.ASYNC)
            public String putValue(String arg1) {
                return value;
            }
            """);

        var cache = newCaffeineCache();
        var service = newObject("$CacheableSync__AopProxy", cache);

        assertThat(call(service, "putValue", "1")).isEqualTo("1");

        assertThat(call(cache, "get", "1")).isEqualTo("1");
    }

    @Test
    public void cacheInvalidateAsyncWithCaffeineIsSync() {
        compileCaffeine("""
            @CacheInvalidate(value = DummyCache.class, mode = CacheMode.ASYNC)
            public void evictValue(String arg1) {
            }
            """);

        var cache = newCaffeineCache();
        call(cache, "put", "1", "1");
        var service = newObject("$CacheableSync__AopProxy", cache);

        call(service, "evictValue", "1");

        assertThat(call(cache, "get", "1")).isNull();
    }

    @Test
    public void cacheInvalidateAllAsyncWithCaffeineIsSync() {
        compileCaffeine("""
            @CacheInvalidateAll(value = DummyCache.class, mode = CacheMode.ASYNC)
            public void evictAll() {
            }
            """);

        var cache = newCaffeineCache();
        call(cache, "put", "1", "1");
        call(cache, "put", "2", "2");
        var service = newObject("$CacheableSync__AopProxy", cache);

        call(service, "evictAll");

        assertThat(call(cache, "get", "1")).isNull();
        assertThat(call(cache, "get", "2")).isNull();
    }

    private void compileRedis(String method) {
        var compileResult = compile(List.of(new CacheAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Cache("dummy")
                public interface DummyCache extends io.koraframework.cache.redis.RedisCache<String, String> { }
                """, """
                public class CacheableSync {
                    public String value = "1";
                    
                    %s
                }
                """.formatted(method));
        compileResult.assertSuccess();
    }

    private void compileCaffeine(String method) {
        var compileResult = compile(List.of(new CacheAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Cache("dummy")
                public interface DummyCache extends CaffeineCache<String, String> { }
                """, """
                public class CacheableSync {
                    public String value = "1";
                    
                    %s
                }
                """.formatted(method));
        compileResult.assertSuccess();
    }

    private Object newRedisCache(BlockingRedisCacheClient client) {
        return newObject("$DummyCache_Impl", CacheRunner.getRedisConfig(), client,
            defaultRedisCacheTelemetryFactory(null, null, null, null),
            stringRedisCacheKeyMapper(), stringRedisCacheValueMapper());
    }

    private Object newCaffeineCache() {
        return newObject("$DummyCache_Impl", CacheRunner.getCaffeineConfig(),
            caffeineCacheFactory(null), defaultCaffeineCacheTelemetryFactory(null, null, null, null));
    }

    @Override
    protected String commonImports() {
        return super.commonImports() +
               """
                   import io.koraframework.cache.annotation.CacheInvalidateAll;
                   import io.koraframework.cache.annotation.CacheMode;
                   """;
    }

    private static Object call(Object target, String method, Object... args) {
        try {
            for (var declaredMethod : target.getClass().getMethods()) {
                if (declaredMethod.getName().equals(method) && declaredMethod.getParameterCount() == args.length
                    && isAssignable(declaredMethod.getParameterTypes(), args)) {
                    return declaredMethod.invoke(target, args);
                }
            }
            throw new IllegalArgumentException("Method " + method + " wasn't found");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isAssignable(Class<?>[] parameterTypes, Object[] args) {
        for (int i = 0; i < parameterTypes.length; i++) {
            if (args[i] != null && !parameterTypes[i].isAssignableFrom(args[i].getClass())) {
                return false;
            }
        }
        return true;
    }

    private static void assertEventually(BooleanSupplier assertion) {
        try {
            for (int i = 0; i < 50; i++) {
                if (assertion.getAsBoolean()) {
                    return;
                }
                Thread.sleep(100);
            }
            fail("Async cache operation didn't complete");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail(e);
        }
    }

    private static final class BlockingRedisCacheClient implements RedisCacheClient {
        private final Map<ByteBuffer, ByteBuffer> cache = new ConcurrentHashMap<>();
        private final RedisCacheClient delegate = CacheRunner.lettuceClient(cache);
        private volatile Mode mode = Mode.NONE;
        private volatile CountDownLatch started = new CountDownLatch(0);
        private volatile CountDownLatch release = new CountDownLatch(0);

        void blockSet() {
            block(Mode.SET);
        }

        void blockDelOne() {
            block(Mode.DEL_ONE);
        }

        void blockDelMany() {
            block(Mode.DEL_MANY);
        }

        void awaitBlocked() {
            try {
                assertTrue(this.started.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail(e);
            }
        }

        void release() {
            this.release.countDown();
        }

        private void block(Mode mode) {
            this.mode = mode;
            this.started = new CountDownLatch(1);
            this.release = new CountDownLatch(1);
        }

        private void awaitRelease(Mode mode) {
            if (this.mode != mode) {
                return;
            }
            try {
                this.started.countDown();
                assertTrue(this.release.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail(e);
            }
        }

        @Override
        public List<byte[]> scan(byte[] prefix) {
            return delegate.scan(prefix);
        }

        @Override
        public byte[] get(byte[] key) {
            return delegate.get(key);
        }

        @Override
        public Map<byte[], byte[]> mget(byte[][] keys) {
            return delegate.mget(keys);
        }

        @Override
        public byte[] getex(byte[] key, long expireAfterMillis) {
            return delegate.getex(key, expireAfterMillis);
        }

        @Override
        public Map<byte[], byte[]> getex(byte[][] keys, long expireAfterMillis) {
            return delegate.getex(keys, expireAfterMillis);
        }

        @Override
        public void set(byte[] key, byte[] value) {
            awaitRelease(Mode.SET);
            delegate.set(key, value);
        }

        @Override
        public void mset(Map<byte[], byte[]> keyAndValue) {
            delegate.mset(keyAndValue);
        }

        @Override
        public void psetex(byte[] key, byte[] value, long expireAfterMillis) {
            awaitRelease(Mode.SET);
            delegate.psetex(key, value, expireAfterMillis);
        }

        @Override
        public void psetex(Map<byte[], byte[]> keyAndValue, long expireAfterMillis) {
            delegate.psetex(keyAndValue, expireAfterMillis);
        }

        @Override
        public long del(byte[] key) {
            awaitRelease(Mode.DEL_ONE);
            return delegate.del(key);
        }

        @Override
        public long del(byte[][] keys) {
            awaitRelease(Mode.DEL_MANY);
            return delegate.del(keys);
        }

        @Override
        public void flushAll() {
            delegate.flushAll();
        }

        private enum Mode {
            NONE,
            SET,
            DEL_ONE,
            DEL_MANY
        }
    }
}
