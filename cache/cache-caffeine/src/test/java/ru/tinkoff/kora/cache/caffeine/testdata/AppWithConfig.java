package ru.tinkoff.kora.cache.caffeine.testdata;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheModule;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.config.common.DefaultConfigExtractorsModule;

@KoraApp
public interface AppWithConfig extends DefaultConfigExtractorsModule, CaffeineCacheModule {

    default Config config() {
        return ConfigFactory.parseString(
            """
                cache {
                  caffeine {
                    sync_cache {
                      maximumSize = 10
                    }
                    mono_cache {
                      maximumSize = 10
                    }
                  }
                }
                """
        ).resolve();
    }

    default MockLifecycle object(CacheableTargetSync cacheableTargetSync, CacheableTargetMono cacheableTargetMono) {
        return new CacheableMockLifecycle(cacheableTargetMono, cacheableTargetSync);
    }
}
