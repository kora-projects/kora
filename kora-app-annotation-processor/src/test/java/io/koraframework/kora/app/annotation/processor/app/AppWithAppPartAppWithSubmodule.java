package io.koraframework.kora.app.annotation.processor.app;

import io.koraframework.common.Component;
import io.koraframework.common.KoraApp;
import io.koraframework.common.annotation.Root;

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

    @io.koraframework.common.Module
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
