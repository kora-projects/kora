package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.Module;
import ru.tinkoff.kora.common.annotation.Root;

@KoraApp
public interface AppWithApp extends App {

    class Class2 {}

    @Root
    default Class2 class2() {
        return new Class2();
    }

    @Module
    interface AppWithAppModule {

        class Class22 {}

        @Root
        default Class22 class22() {
            return new Class22();
        }
    }
}
