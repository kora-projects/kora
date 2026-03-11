package io.koraframework.test.extension.junit5.testdata;

import io.koraframework.common.KoraApp;
import io.koraframework.common.annotation.Root;
import io.koraframework.config.common.Config;
import io.koraframework.config.hocon.HoconConfigModule;

@KoraApp
public interface TestConfigApplication extends HoconConfigModule {
    @Root
    default Object root(Config config) {
        return config;
    }
}
