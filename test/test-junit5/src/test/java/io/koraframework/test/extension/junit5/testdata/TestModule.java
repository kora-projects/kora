package io.koraframework.test.extension.junit5.testdata;

import io.koraframework.common.Module;
import io.koraframework.common.annotation.Root;

@Module
public interface TestModule {

    @Root
    default SomeModuleService someModuleService() {
        return () -> "1";
    }

    interface SomeModuleService {

        String getValue();
    }
}
