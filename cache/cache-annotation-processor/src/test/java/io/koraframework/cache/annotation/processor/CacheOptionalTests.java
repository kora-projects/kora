package io.koraframework.cache.annotation.processor;

import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.cache.caffeine.CaffeineCacheModule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CacheOptionalTests extends AbstractCacheAnnotationProcessorTests implements CaffeineCacheModule {

    @Test
    public void cacheSingleWithOptionalMethodOnly() {
        var compileResult = compile(List.of(new CacheAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Cache("dummy")
                public interface DummyCache extends CaffeineCache<String, String> { }
                """, """
                public class CacheableSync {
                                
                    public String value = "1";
                    
                    @Cacheable(DummyCache.class)
                    public Optional<String> getValueOptional(String arg1) {
                        return value.describeConstable();
                    }
                    
                    @CachePut(value = DummyCache.class, args = {"arg1"})
                    public Optional<String> putValueOptional(BigDecimal arg2, String arg3, String arg1) {
                        return Optional.ofNullable(value);
                    }
                    
                    @CacheInvalidate(DummyCache.class)
                    public void evictValue(String arg1) {
                                
                    }
                }
                """);
        compileResult.assertSuccess();

        var cache = newObject("$DummyCache_Impl", CacheRunner.getCaffeineConfig(),
            caffeineCacheFactory(null), defaultCaffeineCacheTelemetryFactory(null, null, null, null));
        assertThat(cache).isNotNull();

        var service = newObject("$CacheableSync__AopProxy", cache);
        assertThat(service).isNotNull();
    }

    @Test
    public void cacheDoubleWithOptionalMethodOnly() {
        var compileResult = compile(List.of(new CacheAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Cache("dummy1")
                public interface DummyCache1 extends CaffeineCache<String, String> { }
                """, """
                @Cache("dummy2")
                public interface DummyCache2 extends CaffeineCache<String, String> { }
                """, """
                public class CacheableSync {
                            
                    public String value = "1";
                    
                    @Cacheable(DummyCache1.class)
                    @Cacheable(DummyCache2.class)
                    public Optional<String> getValueOptional(String arg1) {
                        return value.describeConstable();
                    }
                    
                    @CachePut(value = DummyCache1.class, args = {"arg1"})
                    @CachePut(value = DummyCache2.class, args = {"arg1"})
                    public Optional<String> putValueOptional(BigDecimal arg2, String arg3, String arg1) {
                        return Optional.ofNullable(value);
                    }
                    
                    @CacheInvalidate(DummyCache1.class)
                    @CacheInvalidate(DummyCache2.class)
                    public void evictValue(String arg1) {
                                
                    }
                }
                """);
        compileResult.assertSuccess();

        var cache1 = newObject("$DummyCache1_Impl", CacheRunner.getCaffeineConfig(),
            caffeineCacheFactory(null), defaultCaffeineCacheTelemetryFactory(null, null, null, null));
        assertThat(cache1).isNotNull();
        var cache2 = newObject("$DummyCache2_Impl", CacheRunner.getCaffeineConfig(),
            caffeineCacheFactory(null), defaultCaffeineCacheTelemetryFactory(null, null, null, null));
        assertThat(cache2).isNotNull();

        var service = newObject("$CacheableSync__AopProxy", cache1, cache2);
        assertThat(service).isNotNull();
    }

    @Test
    public void cacheSingleWithOptionalSignature() {
        var compileResult = compile(List.of(new CacheAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Cache("dummy")
                public interface DummyCache extends CaffeineCache<String, Optional<String>> { }
                """, """
                public class CacheableSync {
                                
                    public String value = "1";
                    
                    @Cacheable(DummyCache.class)
                    public String getValueOptional(String arg1) {
                        return value;
                    }
                    
                    @CachePut(value = DummyCache.class, args = {"arg1"})
                    public String putValueOptional(BigDecimal arg2, String arg3, String arg1) {
                        return value;
                    }
                    
                    @CacheInvalidate(DummyCache.class)
                    public void evictValue(String arg1) {
                                
                    }
                }
                """);
        compileResult.assertSuccess();

        var cache = newObject("$DummyCache_Impl", CacheRunner.getCaffeineConfig(),
            caffeineCacheFactory(null), defaultCaffeineCacheTelemetryFactory(null, null, null, null));
        assertThat(cache).isNotNull();

        var service = newObject("$CacheableSync__AopProxy", cache);
        assertThat(service).isNotNull();
    }

    @Test
    public void cacheDoubleWithOptionalSignature() {
        var compileResult = compile(List.of(new CacheAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Cache("dummy1")
                public interface DummyCache1 extends CaffeineCache<String, String> { }
                """, """
                @Cache("dummy2")
                public interface DummyCache2 extends CaffeineCache<String, String> { }
                """, """
                public class CacheableSync {
                                
                    public String value = "1";
                    
                    @Cacheable(DummyCache1.class)
                    @Cacheable(DummyCache2.class)
                    public String getValueOptional(String arg1) {
                        return value;
                    }
                    
                    @CachePut(value = DummyCache1.class, args = {"arg1"})
                    @CachePut(value = DummyCache2.class, args = {"arg1"})
                    public String putValueOptional(BigDecimal arg2, String arg3, String arg1) {
                        return value;
                    }
                    
                    @CacheInvalidate(DummyCache1.class)
                    @CacheInvalidate(DummyCache2.class)
                    public void evictValue(String arg1) {
                                
                    }
                }
                """);
        compileResult.assertSuccess();

        var cache1 = newObject("$DummyCache1_Impl", CacheRunner.getCaffeineConfig(),
            caffeineCacheFactory(null), defaultCaffeineCacheTelemetryFactory(null, null, null, null));
        assertThat(cache1).isNotNull();
        var cache2 = newObject("$DummyCache2_Impl", CacheRunner.getCaffeineConfig(),
            caffeineCacheFactory(null), defaultCaffeineCacheTelemetryFactory(null, null, null, null));
        assertThat(cache2).isNotNull();

        var service = newObject("$CacheableSync__AopProxy", cache1, cache2);
        assertThat(service).isNotNull();
    }

    @Test
    public void cacheSingleWithOptionalSignatureAndMethod() {
        var compileResult = compile(List.of(new CacheAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Cache("dummy")
                public interface DummyCache extends CaffeineCache<String, Optional<String>> { }
                """, """
                public class CacheableSync {
                                
                    public String value = "1";
                    
                    @Cacheable(DummyCache.class)
                    public Optional<String> getValueOptional(String arg1) {
                        return value.describeConstable();
                    }
                    
                    @CachePut(value = DummyCache.class, args = {"arg1"})
                    public Optional<String> putValueOptional(BigDecimal arg2, String arg3, String arg1) {
                        return Optional.ofNullable(value);
                    }
                    
                    @CacheInvalidate(DummyCache.class)
                    public void evictValue(String arg1) {
                                
                    }
                }
                """);
        compileResult.assertSuccess();

        var cache = newObject("$DummyCache_Impl", CacheRunner.getCaffeineConfig(),
            caffeineCacheFactory(null), defaultCaffeineCacheTelemetryFactory(null, null, null, null));
        assertThat(cache).isNotNull();

        var service = newObject("$CacheableSync__AopProxy", cache);
        assertThat(service).isNotNull();
    }

    @Test
    public void cacheDoubleWithOptionalSignatureAndMethod() {
        var compileResult = compile(List.of(new CacheAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Cache("dummy1")
                public interface DummyCache1 extends CaffeineCache<String, String> { }
                """, """
                @Cache("dummy2")
                public interface DummyCache2 extends CaffeineCache<String, String> { }
                """, """
                public class CacheableSync {
                                
                    public String value = "1";
                    
                    @Cacheable(DummyCache1.class)
                    @Cacheable(DummyCache2.class)
                    public Optional<String> getValueOptional(String arg1) {
                        return value.describeConstable();
                    }
                    
                    @CachePut(value = DummyCache1.class, args = {"arg1"})
                    @CachePut(value = DummyCache2.class, args = {"arg1"})
                    public Optional<String> putValueOptional(BigDecimal arg2, String arg3, String arg1) {
                        return Optional.ofNullable(value);
                    }
                    
                    @CacheInvalidate(DummyCache1.class)
                    @CacheInvalidate(DummyCache2.class)
                    public void evictValue(String arg1) {
                                
                    }
                }
                """);
        compileResult.assertSuccess();

        var cache1 = newObject("$DummyCache1_Impl", CacheRunner.getCaffeineConfig(),
            caffeineCacheFactory(null), defaultCaffeineCacheTelemetryFactory(null, null, null, null));
        assertThat(cache1).isNotNull();
        var cache2 = newObject("$DummyCache2_Impl", CacheRunner.getCaffeineConfig(),
            caffeineCacheFactory(null), defaultCaffeineCacheTelemetryFactory(null, null, null, null));
        assertThat(cache2).isNotNull();

        var service = newObject("$CacheableSync__AopProxy", cache1, cache2);
        assertThat(service).isNotNull();
    }

    @Test
    public void cacheDoubleWithOptionalAndNonOptionalSignatureAndOptionalMethod() {
        var compileResult = compile(List.of(new CacheAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Cache("dummy1")
                public interface DummyCache1 extends CaffeineCache<String, String> { }
                """, """
                @Cache("dummy2")
                public interface DummyCache2 extends CaffeineCache<String, Optional<String>> { }
                """, """
                public class CacheableSync {
                            
                    public String value = "1";
                    
                    @Cacheable(DummyCache1.class)
                    @Cacheable(DummyCache2.class)
                    public Optional<String> getValueOptional(String arg1) {
                        return value.describeConstable();
                    }
                    
                    @CachePut(value = DummyCache1.class, args = {"arg1"})
                    @CachePut(value = DummyCache2.class, args = {"arg1"})
                    public Optional<String> putValueOptional(BigDecimal arg2, String arg3, String arg1) {
                        return Optional.ofNullable(value);
                    }
                    
                    @CacheInvalidate(DummyCache1.class)
                    @CacheInvalidate(DummyCache2.class)
                    public void evictValue(String arg1) {
                                
                    }
                }
                """);
        compileResult.assertSuccess();

        var cache1 = newObject("$DummyCache1_Impl", CacheRunner.getCaffeineConfig(),
            caffeineCacheFactory(null), defaultCaffeineCacheTelemetryFactory(null, null, null, null));
        assertThat(cache1).isNotNull();
        var cache2 = newObject("$DummyCache2_Impl", CacheRunner.getCaffeineConfig(),
            caffeineCacheFactory(null), defaultCaffeineCacheTelemetryFactory(null, null, null, null));
        assertThat(cache2).isNotNull();

        var service = newObject("$CacheableSync__AopProxy", cache1, cache2);
        assertThat(service).isNotNull();
    }

    @Test
    public void cacheDoubleWithOptionalAndNonOptionalSignature() {
        var compileResult = compile(List.of(new CacheAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Cache("dummy1")
                public interface DummyCache1 extends CaffeineCache<String, String> { }
                """, """
                @Cache("dummy2")
                public interface DummyCache2 extends CaffeineCache<String, Optional<String>> { }
                """, """
                public class CacheableSync {
                            
                    public String value = "1";
                    
                    @Cacheable(DummyCache1.class)
                    @Cacheable(DummyCache2.class)
                    public String getValueOptional(String arg1) {
                        return value;
                    }
                    
                    @CachePut(value = DummyCache1.class, args = {"arg1"})
                    @CachePut(value = DummyCache2.class, args = {"arg1"})
                    public String putValueOptional(BigDecimal arg2, String arg3, String arg1) {
                        return value;
                    }
                    
                    @CacheInvalidate(DummyCache1.class)
                    @CacheInvalidate(DummyCache2.class)
                    public void evictValue(String arg1) {
                                
                    }
                }
                """);
        compileResult.assertSuccess();

        var cache1 = newObject("$DummyCache1_Impl", CacheRunner.getCaffeineConfig(),
            caffeineCacheFactory(null), defaultCaffeineCacheTelemetryFactory(null, null, null, null));
        assertThat(cache1).isNotNull();
        var cache2 = newObject("$DummyCache2_Impl", CacheRunner.getCaffeineConfig(),
            caffeineCacheFactory(null), defaultCaffeineCacheTelemetryFactory(null, null, null, null));
        assertThat(cache2).isNotNull();

        var service = newObject("$CacheableSync__AopProxy", cache1, cache2);
        assertThat(service).isNotNull();
    }

    private Object newCaffeineCache(String impl) {
        return newObject(impl, CacheRunner.getCaffeineConfig(), caffeineCacheFactory(null), defaultCaffeineCacheTelemetryFactory(null, null, null, null));
    }

    private Object compileTwoLevelCacheableReturningNull(String cache1Value, String cache2Value) {
        compile(List.of(new CacheAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Cache("dummy1")
                public interface DummyCache1 extends CaffeineCache<String, %s> { }
                """.formatted(cache1Value), """
                @Cache("dummy2")
                public interface DummyCache2 extends CaffeineCache<String, %s> { }
                """.formatted(cache2Value), """
                public class CacheableSync {
                    public int calls = 0;

                    @Cacheable(DummyCache1.class)
                    @Cacheable(DummyCache2.class)
                    public String get(String arg1) {
                        calls++;
                        return null;
                    }
                }
                """).assertSuccess();
        return newObject("$CacheableSync__AopProxy", newCaffeineCache("$DummyCache1_Impl"), newCaffeineCache("$DummyCache2_Impl"));
    }

    private int calls(Object service) throws Exception {
        return service.getClass().getField("calls").getInt(service);
    }

    @Test
    public void cacheDoubleOptionalCachesKeepAbsentResult() throws Exception {
        var service = compileTwoLevelCacheableReturningNull("Optional<String>", "Optional<String>");

        assertThat((Object) invoke(service, "get", "k")).isNull();
        assertThat((Object) invoke(service, "get", "k")).isNull();
        assertThat(calls(service)).isEqualTo(1);
    }

    @Test
    public void cacheDoubleSecondLevelOptionalCacheKeepsAbsentResult() throws Exception {
        var service = compileTwoLevelCacheableReturningNull("String", "Optional<String>");

        assertThat((Object) invoke(service, "get", "k")).isNull();
        assertThat((Object) invoke(service, "get", "k")).isNull();
        assertThat(calls(service)).isEqualTo(1);
    }

    @Test
    public void cacheDoubleSecondLevelOptionalCacheBackFillsFirstLevelOnlyWithPresentValue() throws Exception {
        var service = compileTwoLevelCacheableReturningNull("String", "Optional<String>");
        var dummy1 = (io.koraframework.cache.Cache<Object, Object>) fieldOfType(service, "DummyCache1");
        var dummy2 = (io.koraframework.cache.Cache<Object, Object>) fieldOfType(service, "DummyCache2");

        dummy2.put("present", java.util.Optional.of("v"));
        assertThat((Object) invoke(service, "get", "present")).isEqualTo("v");
        assertThat(dummy1.get("present")).isEqualTo("v");

        assertThat((Object) invoke(service, "get", "absent")).isNull();
        assertThat(dummy1.get("absent")).isNull();
        assertThat(dummy2.get("absent")).isEqualTo(java.util.Optional.empty());
        assertThat(calls(service)).isEqualTo(1);
    }

    @Test
    public void cacheDoubleFirstLevelOptionalCacheIsBackFilledFromSecondLevel() throws Exception {
        var service = compileTwoLevelCacheableReturningNull("Optional<String>", "String");
        var dummy1 = (io.koraframework.cache.Cache<Object, Object>) fieldOfType(service, "DummyCache1");
        var dummy2 = (io.koraframework.cache.Cache<Object, Object>) fieldOfType(service, "DummyCache2");

        dummy2.put("k", "v");
        assertThat((Object) invoke(service, "get", "k")).isEqualTo("v");
        assertThat(dummy1.get("k")).isEqualTo(java.util.Optional.of("v"));
        assertThat(calls(service)).isEqualTo(0);
    }

    private static Object fieldOfType(Object service, String typeSimpleName) throws Exception {
        for (var f : service.getClass().getDeclaredFields()) {
            if (f.getType().getSimpleName().equals(typeSimpleName)) {
                f.setAccessible(true);
                return f.get(service);
            }
        }
        throw new IllegalStateException("No field of type " + typeSimpleName);
    }
}
