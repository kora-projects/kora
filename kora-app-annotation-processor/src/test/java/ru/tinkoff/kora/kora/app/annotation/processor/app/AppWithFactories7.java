package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.Tag;

@KoraApp
public interface AppWithFactories7 {

    default int intComponent() {
        return 0;
    }

    default <T> GenericClass<T> factory1(TypeRef<T> typeRef, int dependency) {
        throw new IllegalStateException();
    }

    @Tag(Class1.class)
    default <T> GenericClass<T> factory2(TypeRef<T> typeRef, int dependency) {
        return new GenericClass<>();
    }

    default Class1 class1(@Tag(Class1.class) GenericClass<Class1> class1) {
        return new Class1();
    }

    class GenericClass<T> {}

    class Class1 implements MockLifecycle {}
}
