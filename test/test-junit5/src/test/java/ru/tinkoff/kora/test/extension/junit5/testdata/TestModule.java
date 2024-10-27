package ru.tinkoff.kora.test.extension.junit5.testdata;

import ru.tinkoff.kora.common.Module;
import ru.tinkoff.kora.common.annotation.Root;

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
