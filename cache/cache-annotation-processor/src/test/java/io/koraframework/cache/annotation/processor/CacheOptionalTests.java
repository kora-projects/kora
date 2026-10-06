package io.koraframework.cache.annotation.processor;

import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.cache.caffeine.CaffeineCacheModule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    public void cacheSingleWithCheckedException() {
        var compileResult = compile(List.of(new CacheAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Cache("dummy")
                public interface DummyCache extends CaffeineCache<String, String> { }
                """, """
                @Cache("dummy_optional")
                public interface DummyOptionalCache extends CaffeineCache<String, Optional<String>> { }
                """, """
                public class CacheableSync {

                    public String value = "1";

                    @Cacheable(DummyCache.class)
                    public String getValue(String arg1) throws java.io.IOException {
                        if (arg1.isEmpty()) throw new java.io.IOException("empty");
                        return value;
                    }

                    @Cacheable(DummyCache.class)
                    public Optional<String> getValueOptional(String arg1) throws java.io.IOException {
                        if (arg1.isEmpty()) throw new java.io.IOException("empty");
                        return Optional.ofNullable(value);
                    }

                    @Cacheable(DummyOptionalCache.class)
                    @Nullable
                    public String getValueNullable(String arg1) throws java.io.IOException {
                        if (arg1.isEmpty()) throw new java.io.IOException("empty");
                        return value;
                    }
                }
                """);
        compileResult.assertSuccess();

        var cache = newObject("$DummyCache_Impl", CacheRunner.getCaffeineConfig(),
            caffeineCacheFactory(null), defaultCaffeineCacheTelemetryFactory(null, null, null, null));
        var optionalCache = newObject("$DummyOptionalCache_Impl", CacheRunner.getCaffeineConfig(),
            caffeineCacheFactory(null), defaultCaffeineCacheTelemetryFactory(null, null, null, null));
        var service = newObject("$CacheableSync__AopProxy", cache, optionalCache);

        assertThat(invoke(service, "getValue", "a")).isEqualTo("1");
        assertThat(invoke(service, "getValueOptional", "b")).isEqualTo(Optional.of("1"));
        assertThat(invoke(service, "getValueNullable", "c")).isEqualTo("1");
        setValue(service, "2");
        assertThat(invoke(service, "getValue", "a")).isEqualTo("1");
        assertThat(invoke(service, "getValueOptional", "b")).isEqualTo(Optional.of("1"));
        assertThat(invoke(service, "getValueNullable", "c")).isEqualTo("1");

        for (var method : List.of("getValue", "getValueOptional", "getValueNullable")) {
            assertThatThrownBy(() -> invoke(service, method, ""))
                .cause().cause().isInstanceOf(java.io.IOException.class).hasMessage("empty");
        }
    }

    private static void setValue(Object service, String value) {
        try {
            service.getClass().getField("value").set(service, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
