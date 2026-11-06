package io.koraframework.cache.annotation.processor;

import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.cache.Cache;
import io.koraframework.cache.caffeine.CaffeineCacheModule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CacheAopGeneratedCodeTests extends AbstractCacheAnnotationProcessorTests implements CaffeineCacheModule {

    @Test
    public void cacheableThreeLevelsWithSameKeyType() {
        compileThreeCaches("""
            public class CacheableSync {
                @Cacheable(Cache1.class)
                @Cacheable(Cache2.class)
                @Cacheable(Cache3.class)
                public String getValue(String arg1) {
                    return "computed";
                }
            }
            """);
        compileResult.assertSuccess();

        var cache1 = newCache("$Cache1_Impl");
        var cache2 = newCache("$Cache2_Impl");
        var cache3 = newCache("$Cache3_Impl");
        var service = newObject("$CacheableSync__AopProxy", cache1, cache2, cache3);

        cache3.put("1", "cached");
        assertThat(invoke(service, "getValue", "1")).isEqualTo("cached");
        assertThat(cache1.get("1")).isEqualTo("cached");
        assertThat(cache2.get("1")).isEqualTo("cached");

        assertThat(invoke(service, "getValue", "2")).isEqualTo("computed");
        assertThat(cache1.get("2")).isEqualTo("computed");
        assertThat(cache2.get("2")).isEqualTo("computed");
        assertThat(cache3.get("2")).isEqualTo("computed");
    }

    @Test
    public void cachePutThreeLevelsWithSameKeyType() {
        compileThreeCaches("""
            public class CacheableSync {
                @CachePut(Cache1.class)
                @CachePut(Cache2.class)
                @CachePut(Cache3.class)
                public String putValue(String arg1) {
                    return "computed";
                }
            }
            """);
        compileResult.assertSuccess();

        var cache1 = newCache("$Cache1_Impl");
        var cache2 = newCache("$Cache2_Impl");
        var cache3 = newCache("$Cache3_Impl");
        var service = newObject("$CacheableSync__AopProxy", cache1, cache2, cache3);

        assertThat(invoke(service, "putValue", "1")).isEqualTo("computed");
        assertThat(cache1.get("1")).isEqualTo("computed");
        assertThat(cache2.get("1")).isEqualTo("computed");
        assertThat(cache3.get("1")).isEqualTo("computed");
    }

    @Test
    public void cacheInvalidateThreeLevelsWithSameKeyType() {
        compileThreeCaches("""
            public class CacheableSync {
                @CacheInvalidate(Cache1.class)
                @CacheInvalidate(Cache2.class)
                @CacheInvalidate(Cache3.class)
                public void evictValue(String arg1) {
                }
            }
            """);
        compileResult.assertSuccess();

        var cache1 = newCache("$Cache1_Impl");
        var cache2 = newCache("$Cache2_Impl");
        var cache3 = newCache("$Cache3_Impl");
        var service = newObject("$CacheableSync__AopProxy", cache1, cache2, cache3);
        cache1.put("1", "cached");
        cache2.put("1", "cached");
        cache3.put("1", "cached");

        invoke(service, "evictValue", "1");
        assertThat(cache1.get("1")).isNull();
        assertThat(cache2.get("1")).isNull();
        assertThat(cache3.get("1")).isNull();
    }

    @Test
    public void cacheInvalidateNonVoidWithParameterNamedValue() {
        compile(List.of(new CacheAnnotationProcessor(), new AopAnnotationProcessor()), """
            @Cache("cache1")
            public interface Cache1 extends CaffeineCache<String, String> { }
            """, """
            public class CacheableSync {
                @CacheInvalidate(value = Cache1.class, args = "id")
                public String update(String id, String value) {
                    return value;
                }
            }
            """);
        compileResult.assertSuccess();

        var cache = newCache("$Cache1_Impl");
        var service = newObject("$CacheableSync__AopProxy", cache);
        cache.put("1", "cached");
        cache.put("new", "other");

        assertThat(invoke(service, "update", "1", "new")).isEqualTo("new");
        assertThat(cache.get("1")).isNull();
        assertThat(cache.get("new")).isEqualTo("other");
    }

    private void compileThreeCaches(String service) {
        compile(List.of(new CacheAnnotationProcessor(), new AopAnnotationProcessor()), """
            @Cache("cache1")
            public interface Cache1 extends CaffeineCache<String, String> { }
            """, """
            @Cache("cache2")
            public interface Cache2 extends CaffeineCache<String, String> { }
            """, """
            @Cache("cache3")
            public interface Cache3 extends CaffeineCache<String, String> { }
            """, service);
    }

    @SuppressWarnings("unchecked")
    private Cache<String, String> newCache(String implName) {
        return (Cache<String, String>) newObject(implName, CacheRunner.getCaffeineConfig(),
            caffeineCacheFactory(null), defaultCaffeineCacheTelemetryFactory(null, null, null, null));
    }
}
