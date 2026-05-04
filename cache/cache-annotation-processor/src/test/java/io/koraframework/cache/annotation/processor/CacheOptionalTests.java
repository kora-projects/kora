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
            caffeineCacheFactory(null));
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
            caffeineCacheFactory(null));
        assertThat(cache1).isNotNull();
        var cache2 = newObject("$DummyCache2_Impl", CacheRunner.getCaffeineConfig(),
            caffeineCacheFactory(null));
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
            caffeineCacheFactory(null));
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
            caffeineCacheFactory(null));
        assertThat(cache1).isNotNull();
        var cache2 = newObject("$DummyCache2_Impl", CacheRunner.getCaffeineConfig(),
            caffeineCacheFactory(null));
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
            caffeineCacheFactory(null));
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
            caffeineCacheFactory(null));
        assertThat(cache1).isNotNull();
        var cache2 = newObject("$DummyCache2_Impl", CacheRunner.getCaffeineConfig(),
            caffeineCacheFactory(null));
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
            caffeineCacheFactory(null));
        assertThat(cache1).isNotNull();
        var cache2 = newObject("$DummyCache2_Impl", CacheRunner.getCaffeineConfig(),
            caffeineCacheFactory(null));
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
            caffeineCacheFactory(null));
        assertThat(cache1).isNotNull();
        var cache2 = newObject("$DummyCache2_Impl", CacheRunner.getCaffeineConfig(),
            caffeineCacheFactory(null));
        assertThat(cache2).isNotNull();

        var service = newObject("$CacheableSync__AopProxy", cache1, cache2);
        assertThat(service).isNotNull();
    }
}
