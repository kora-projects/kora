package io.koraframework.test.extension.junit5.testdata;

import io.koraframework.common.annotation.Root;

public interface TestExtendModule {

    @Root
    default SomeExtendModuleService someExtendModuleService() {
        return () -> "1";
    }

    interface SomeExtendModuleService {

        String getValue();
    }
}
