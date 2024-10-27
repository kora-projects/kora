package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;

@KoraApp
public interface AppWithAppPartAppWithSubmodule extends AppWithAppPart {

    @Root
    @Component
    class AppClass1 {}

    @Root
    @Component
    class AppClass2 {}

    @Root
    default AppClass1 appClass1() {
        return new AppClass1();
    }

    @ru.tinkoff.kora.common.Module
    interface AppModule {

        class AppModuleClass1 {}

        @Root
        @Component
        class AppModuleClass2 {}

        @Root
        default AppModuleClass1 appModuleClass1() {
            return new AppModuleClass1();
        }
    }
}
