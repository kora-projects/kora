package io.koraframework.cache.annotation.processor;

import io.koraframework.annotation.processor.common.TestUtils;
import io.koraframework.annotation.processor.common.TestUtils.CompilationErrorException;
import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.cache.annotation.processor.testcache.DummyCacheTagged;
import io.koraframework.cache.annotation.processor.testcache.DummyInheritFinal;
import io.koraframework.cache.annotation.processor.testcache.DummyInheritMediator;
import io.koraframework.cache.annotation.processor.testdata.sync.*;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CacheAnnotationProcessorTests extends AbstractCacheAnnotationProcessorTests {

    @Test
    void cacheKeyMultipleAnnotationsOneMethod() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableSyncWrongAnnotationMany.class, new AopAnnotationProcessor()));
    }

    @Test
    void cacheKeyArgumentMissing() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableSyncWrongArgumentMissing.class, new AopAnnotationProcessor()));
    }

    @Test
    void cacheKeyMapper() {
        assertDoesNotThrow(() -> TestUtils.annotationProcess(CacheableSyncMapper.class, new AopAnnotationProcessor()));
    }

    @Test
    void cacheAsyncMode() {
        assertDoesNotThrow(() -> TestUtils.annotationProcess(CacheableAsync.class, new AopAnnotationProcessor()));
    }

    @Test
    void cacheTaggedRedisKeyMapper() {
        assertDoesNotThrow(() -> TestUtils.annotationProcess(DummyCacheTagged.class, new CacheAnnotationProcessor()));
    }

    @Test
    void cacheInheritFinalCacheScanner() {
        assertDoesNotThrow(() -> TestUtils.annotationProcess(DummyInheritFinal.class, new CacheAnnotationProcessor()));
    }

    @Test
    void cacheInheritMediatorCacheScanner() {
        assertDoesNotThrow(() -> TestUtils.annotationProcess(DummyInheritMediator.class, new CacheAnnotationProcessor()));
    }

    @Test
    void cacheKeyArgumentWrongOrderMapperRequired() {
        assertDoesNotThrow(() -> TestUtils.annotationProcess(CacheableSyncWrongArgumentOrder.class, new AopAnnotationProcessor()));
    }

    @Test
    void cacheKeyArgumentWrongTypeMapperRequired() {
        assertDoesNotThrow(() -> TestUtils.annotationProcess(CacheableSyncWrongArgumentType.class, new AopAnnotationProcessor()));
    }

    @Test
    void cacheNamePatternMismatch() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableSyncWrongName.class, new CacheAnnotationProcessor()));
    }

    @Test
    void cacheGetForVoidSignature() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableSyncWrongGetVoid.class, new AopAnnotationProcessor()));
    }

    @Test
    void cachePutForVoidSignature() {
        assertThrows(CompilationErrorException.class, () -> TestUtils.annotationProcess(CacheableSyncWrongPutVoid.class, new AopAnnotationProcessor()));
    }

    @Test
    public void testInnerClassCache() {
        compile(List.of(new CacheAnnotationProcessor()), """
            public interface OuterType {
              @io.koraframework.cache.annotation.Cache("test")
              interface MyCache extends io.koraframework.cache.caffeine.CaffeineCache<String, String>{}
            }
            """);
        compileResult.assertSuccess();
    }

    @Test
    void cacheableWithCacheExtendingExtraInterface() {
        compile(List.of(new AopAnnotationProcessor(), new CacheAnnotationProcessor()), """
            public interface Marker {}
            """, """
            @Cache("c1")
            public interface C1 extends CaffeineCache<String, String>, Marker {}
            """, """
            @Component
            public class Svc {
                @Cacheable(C1.class)
                public String get(String id) { return id; }
            }
            """);
        compileResult.assertSuccess();
    }

    @Test
    void redisRecordKeyWithCacheExtendingExtraInterface() {
        compile(List.of(new CacheAnnotationProcessor()), """
            public interface Marker {}
            """, """
            @Cache("c1")
            public interface C1 extends io.koraframework.cache.redis.RedisCache<C1.Key, String>, Marker {
                record Key(String a, String b) {}
            }
            """);
        compileResult.assertSuccess();
    }

    @Test
    void cacheableWithPartiallyBoundParentCache() {
        compile(List.of(new AopAnnotationProcessor(), new CacheAnnotationProcessor()), """
            public interface StringKeyCache<V> extends CaffeineCache<String, V> {}
            """, """
            @Cache("c1")
            public interface C1 extends StringKeyCache<Integer> {}
            """, """
            @Component
            public class Svc {
                @Cacheable(C1.class)
                public Integer get(String id) { return 1; }
            }
            """);
        compileResult.assertSuccess();
    }

    @Test
    void redisCacheThroughGenericParentInterface() {
        compile(List.of(new CacheAnnotationProcessor()), """
            public interface BaseRedis<K, V> extends io.koraframework.cache.redis.RedisCache<K, V> {}
            """, """
            @Cache("c1")
            public interface C1 extends BaseRedis<C1.Key, String> {
                record Key(String a, String b) {}
            }
            """);
        compileResult.assertSuccess();
    }

    @Test
    void redisRecordKeyWithPrimitiveComponent() {
        compile(List.of(new AopAnnotationProcessor(), new CacheAnnotationProcessor()), """
            @Cache("c1")
            public interface C1 extends io.koraframework.cache.redis.RedisCache<C1.Key, String> {
                record Key(String tenant, long id) {}
            }
            """, """
            @Component
            public class Svc {
                @Cacheable(C1.class)
                public String get(String tenant, long id) { return tenant + id; }
            }
            """);
        compileResult.assertSuccess();
    }

    @Test
    void cacheKeyConstructorWithBoxedComponentForPrimitiveArgs() {
        compile(List.of(new AopAnnotationProcessor(), new CacheAnnotationProcessor()), """
            @Cache("c1")
            public interface C1 extends CaffeineCache<C1.Key, String> {
                record Key(String tenant, Long id) {}
            }
            """, """
            @Component
            public class Svc {
                @Cacheable(C1.class)
                public String get(String tenant, long id) { return tenant + id; }
            }
            """);
        compileResult.assertSuccess();
        var constructor = loadClass("$Svc__AopProxy").getDeclaredConstructors()[0];
        assertEquals(1, constructor.getParameterCount(), "Key constructor expected, but proxy requires " + List.of(constructor.getParameterTypes()));
    }

    @Test
    void cacheKeyMapperForPrimitiveArgs() {
        compile(List.of(new AopAnnotationProcessor(), new CacheAnnotationProcessor()), """
            @Cache("c1")
            public interface C1 extends CaffeineCache<C1.Key, String> {
                record Key(String tenant, Long id) {}
            }
            """, """
            @Component
            public class Svc {
                @Cacheable(C1.class)
                public String get(String tenant, int id) { return tenant + id; }
            }
            """);
        compileResult.assertSuccess();
        var constructor = loadClass("$Svc__AopProxy").getDeclaredConstructors()[0];
        assertEquals(2, constructor.getParameterCount(), "CacheKeyMapper2 expected, but proxy requires " + List.of(constructor.getParameterTypes()));
    }

    @Test
    void cacheKeyMapperForBoxedArgsAndPrimitiveComponent() {
        compile(List.of(new AopAnnotationProcessor(), new CacheAnnotationProcessor()), """
            @Cache("c1")
            public interface C1 extends CaffeineCache<C1.Key, String> {
                record Key(String tenant, long id) {}
            }
            """, """
            @Component
            public class Svc {
                @Cacheable(C1.class)
                public String get(String tenant, Long id) { return tenant + id; }
            }
            """);
        compileResult.assertSuccess();
        var constructor = loadClass("$Svc__AopProxy").getDeclaredConstructors()[0];
        assertEquals(2, constructor.getParameterCount(), "CacheKeyMapper2 expected (no unboxing into the Key constructor), but proxy requires " + List.of(constructor.getParameterTypes()));
    }

    @Test
    void twoRedisCachesSharingRecordKey() {
        compile(List.of(new KoraAppProcessor(), new CacheAnnotationProcessor()), """
            public record UserKey(String tenant, String id) {}
            """, """
            @Cache("cache.users")
            public interface UserCache extends io.koraframework.cache.redis.RedisCache<UserKey, String> {}
            """, """
            @Cache("cache.profiles")
            public interface ProfileCache extends io.koraframework.cache.redis.RedisCache<UserKey, String> {}
            """, """
            @KoraApp
            public interface App extends io.koraframework.cache.redis.RedisCacheModule, io.koraframework.config.common.mapper.ConfigValueMapperModule {
                default io.koraframework.config.common.Config config() {
                    return io.koraframework.config.common.util.ConfigMappingUtils.fromMap(java.util.Map.of("cache", java.util.Map.of(
                        "users", java.util.Map.of("keyPrefix", "u"),
                        "profiles", java.util.Map.of("keyPrefix", "p")
                    )));
                }

                default io.koraframework.cache.redis.RedisCacheClient redisCacheClient() {
                    return org.mockito.Mockito.mock(io.koraframework.cache.redis.RedisCacheClient.class);
                }

                @Root
                default String root(UserCache users, ProfileCache profiles) { return ""; }
            }
            """);
        compileResult.assertSuccess();
        assertDoesNotThrow(() -> loadGraph("App").close());
    }

    @Test
    void redisCacheWithRecordKeyWhoseMapperModuleIsOnClasspath() {
        // LibraryRedisKey and its $LibraryRedisKey_RedisCacheKeyMapperModule come from another compilation (test classpath)
        // and the app does not wire that module, so the app's own compilation must generate the mapper module again
        compile(List.of(new KoraAppProcessor(), new CacheAnnotationProcessor()), """
            @Cache("cache.profiles")
            public interface ProfileCache extends io.koraframework.cache.redis.RedisCache<io.koraframework.cache.annotation.processor.testcache.LibraryRedisKey, String> {}
            """, """
            @KoraApp
            public interface App extends io.koraframework.cache.redis.RedisCacheModule, io.koraframework.config.common.mapper.ConfigValueMapperModule {
                default io.koraframework.config.common.Config config() {
                    return io.koraframework.config.common.util.ConfigMappingUtils.fromMap(java.util.Map.of("cache", java.util.Map.of(
                        "profiles", java.util.Map.of("keyPrefix", "p")
                    )));
                }

                default io.koraframework.cache.redis.RedisCacheClient redisCacheClient() {
                    return org.mockito.Mockito.mock(io.koraframework.cache.redis.RedisCacheClient.class);
                }

                @Root
                default String root(ProfileCache profiles) { return ""; }
            }
            """);
        compileResult.assertSuccess();
        assertDoesNotThrow(() -> loadGraph("App").close());
    }
}
