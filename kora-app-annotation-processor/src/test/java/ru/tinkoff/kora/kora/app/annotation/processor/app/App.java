package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.Module;
import ru.tinkoff.kora.common.annotation.Root;

@KoraApp
public interface App {

    class Class1 {}

    @Root
    default Class1 class1() {
        return new Class1();
    }

    @Module
    interface AppModule {

        class Class11 {}

        @Root
        default Class11 class11() {
            return new Class11();
        }
    }
}
