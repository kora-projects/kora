package ru.tinkoff.kora.test.extension.junit5.testdata;

import ru.tinkoff.kora.common.annotation.Root;

public interface TestExtendModule {

    @Root
    default SomeExtendModuleService someExtendModuleService() {
        return () -> "1";
    }

    interface SomeExtendModuleService {

        String getValue();
    }
}
