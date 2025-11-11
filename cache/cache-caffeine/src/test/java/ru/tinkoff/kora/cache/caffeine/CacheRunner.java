package ru.tinkoff.kora.cache.caffeine;

import org.junit.jupiter.api.Assertions;
import org.mockito.Mockito;
import ru.tinkoff.kora.cache.caffeine.testdata.DummyCache;

import static org.mockito.Mockito.when;

abstract class CacheRunner extends Assertions implements CaffeineCacheModule {
    public static CaffeineCacheConfig getConfig() {
        var config = Mockito.mock(CaffeineCacheConfig.class);
        var telemetry = Mockito.mock(CaffeineCacheConfig.CaffeineTelemetryConfig.class);
        when(config.telemetry()).thenReturn(telemetry);
        when(config.maximumSize()).thenReturn(100_000L);
        when(config.expireAfterAccess()).thenReturn(null);
        when(config.expireAfterWrite()).thenReturn(null);
        when(telemetry.metrics()).thenReturn(new $CaffeineCacheConfig_CaffeineTelemetryConfig_CaffeineMetricsConfig_ConfigValueExtractor.CaffeineMetricsConfig_Defaults());
        when(telemetry.logging()).thenReturn(new $CaffeineCacheConfig_CaffeineTelemetryConfig_CaffeineLoggingConfig_ConfigValueExtractor.CaffeineLoggingConfig_Defaults());
        return config;
    }
//    public static CaffeineCacheConfig getConfig() {
//        return new CaffeineCacheConfig() {
//            @Nullable
//            @Override
//            public Duration expireAfterWrite() {
//                return null;
//            }
//
//            @Nullable
//            @Override
//            public Duration expireAfterAccess() {
//                return null;
//            }
//
//            @Nullable
//            @Override
//            public Integer initialSize() {
//                return null;
//            }
//
//            @Override
//            public CaffeineTelemetryConfig telemetry() {
//                return new $CaffeineCacheConfig_CaffeineTelemetryConfig_ConfigValueExtractor.CaffeineTelemetryConfig_Impl(
//                    new $CaffeineCacheConfig_CaffeineTelemetryConfig_CaffeineMetricsConfig_ConfigValueExtractor.CaffeineMetricsConfig_Defaults(),
//                    new $CaffeineCacheConfig_CaffeineTelemetryConfig_CaffeineLoggingConfig_ConfigValueExtractor.CaffeineLoggingConfig_Defaults()
//                );
//            }
//        };
//    }

    protected DummyCache createCache() {
        try {
            return new DummyCache(getConfig(), caffeineCacheFactory(null));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
