package io.koraframework.kora.app.annotation.processor.app;

import io.koraframework.application.graph.TypeRef;
import io.koraframework.common.KoraApp;
import io.koraframework.common.annotation.Root;

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
