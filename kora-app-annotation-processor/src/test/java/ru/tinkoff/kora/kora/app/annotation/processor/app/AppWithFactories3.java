package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;

@KoraApp
public interface AppWithFactories3 {
    default int intComponent() {
        return 0;
    }

    default <T> GenericClass<T> factory(TypeRef<T> typeRef, int dependency) {
        return new GenericClass<>();
    }

    default GenericClass<Class1> genericClass1() {
        return new GenericClass<>();
    }

    @Root
    default Class1 class1(GenericClass<Class1> class1) {
        return new Class1();
    }

    class GenericClass<T> {}

    class Class1 {}
}
