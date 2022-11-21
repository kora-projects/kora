package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.common.KoraApp;

@KoraApp
public interface AppWithFactories1 {

    default int intComponent() {
        return 0;
    }

    default <T> GenericClass<T> factory(TypeRef<T> typeRef, int dependency) {
        return new GenericClass<>();
    }

    default Class1 class1(GenericClass<Class1> class1) {
        return new Class1();
    }

    class GenericClass<T> {}

    class Class1 implements MockLifecycle {}
}
