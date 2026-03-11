package io.koraframework.kora.app.annotation.processor.app;

import io.koraframework.common.KoraApp;
import io.koraframework.common.Module;
import io.koraframework.common.annotation.Root;

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
